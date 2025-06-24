import datetime
import hashlib
import time
from typing import List, Optional, Dict, Any
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, BackgroundTasks, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, validator
import numpy as np
import asyncio
from aiobreaker import CircuitBreaker, CircuitBreakerListener
from prometheus_fastapi_instrumentator import Instrumentator
import logging

from .core.config import get_settings
from .core.model import embed_sentences, _executor, _dim
from .core.cache import cache

# 로깅 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

s = get_settings()

# ──────────────────────── Lifespan ────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    """애플리케이션 수명 주기 관리"""
    # 시작 시
    try:
        # 모델 워밍업
        test_text = "warm up"
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(_executor, embed_sentences, [test_text])
        logger.info(f"✅ Model warmed up. Dimension: {_dim}")

        # 캐시 연결 확인
        await cache.ping()
        logger.info("✅ Cache connected")

    except Exception as e:
        logger.error(f"❌ Startup failed: {e}")

    yield

    # 종료 시
    logger.info("Shutting down ML service...")

app = FastAPI(
    title="ML Embedding Service",
    version="2.1.0",
    docs_url="/docs",
    lifespan=lifespan
)

# CORS 미들웨어
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# 요청 로깅 미들웨어
@app.middleware("http")
async def log_requests(request: Request, call_next):
    start_time = time.time()
    response = await call_next(request)
    process_time = time.time() - start_time

    logger.info(
        f"{request.method} {request.url.path} "
        f"status={response.status_code} "
        f"duration={process_time:.3f}s"
    )

    response.headers["X-Process-Time"] = str(process_time)
    return response

# ──────────────────────── Circuit Breaker ────────────────────────
class CBListener(CircuitBreakerListener):
    async def state_change(self, cb, old_state, new_state):
        logger.warning(f"[Circuit Breaker] {old_state.name} → {new_state.name}")

        # 상태 변경 시 캐시에 기록
        await cache.set(
            "cb:state_change:last",
            {
                "from": old_state.name,
                "to": new_state.name,
                "timestamp": datetime.datetime.utcnow().isoformat()
            },
            ttl=3600
        )

cb = CircuitBreaker(
    fail_max=int(s.cb_failure_rate * 100),
    timeout_duration=datetime.timedelta(seconds=s.cb_reset_timeout),
    listeners=[CBListener()],
)

# ──────────────────────────── DTOs ───────────────────────────────
class EmbedRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=2048)
    cache_key: Optional[str] = Field(None, description="커스텀 캐시 키")

    @validator('text')
    def validate_text(cls, v):
        if not v or not v.strip():
            raise ValueError("텍스트가 비어있습니다")
        return v.strip()

class BatchEmbedRequest(BaseModel):
    texts: List[str] = Field(..., min_items=1, max_items=100)
    cache_keys: Optional[List[str]] = None

    @validator('texts')
    def validate_texts(cls, v):
        cleaned = []
        for text in v:
            if text and text.strip():
                cleaned.append(text.strip()[:2048])  # 길이 제한
        if not cleaned:
            raise ValueError("유효한 텍스트가 없습니다")
        return cleaned

class EmbedResponse(BaseModel):
    vector: List[float] = Field(..., description=f"임베딩 벡터({_dim}차원)")
    cached: bool = Field(False, description="캐시에서 조회됨")
    dimension: int = Field(_dim, description="벡터 차원")
    processing_time_ms: Optional[float] = None

class BatchEmbedResponse(BaseModel):
    vectors: List[List[float]]
    cached_count: int
    dimension: int = Field(_dim)
    processing_time_ms: Optional[float] = None
    batch_size: int

class SimilarityRequest(BaseModel):
    vector1: List[float]
    vector2: List[float]

    @validator('vector1', 'vector2')
    def validate_vector_dimension(cls, v):
        if len(v) != _dim:
            raise ValueError(f"벡터 차원이 {_dim}이어야 합니다. 입력: {len(v)}")
        return v

class SimilarityResponse(BaseModel):
    cosine_similarity: float
    euclidean_distance: float
    dot_product: float

class HealthResponse(BaseModel):
    status: str
    model_loaded: bool
    cache_healthy: bool
    dimension: int
    model_name: str
    uptime_seconds: float

# 글로벌 변수 (업타임 추적)
_start_time = time.time()

# ───────────────────────── 유틸리티 함수 ─────────────────────────
async def update_cache_stats(hit: bool):
    """캐시 통계 업데이트"""
    try:
        key = f"stats:{'hits' if hit else 'misses'}"
        await cache.incr(key)

        # 시간별 통계도 업데이트
        hour_key = f"stats:hourly:{datetime.datetime.utcnow().strftime('%Y%m%d%H')}"
        field = 'hits' if hit else 'misses'
        await cache.hincr(hour_key, field)
        await cache.expire(hour_key, 86400)  # 24시간 후 만료
    except:
        pass  # 통계 실패는 무시

def normalize_vector(vector: np.ndarray) -> np.ndarray:
    """벡터 정규화"""
    norm = np.linalg.norm(vector)
    if norm == 0:
        return vector
    return vector / norm

# ───────────────────────── APIs ─────────────────────────────────
@app.post("/embed", response_model=EmbedResponse)
@cb
async def embed(req: EmbedRequest, background_tasks: BackgroundTasks):
    """단일 텍스트 임베딩 생성"""
    start_time = time.time()

    # 캐시 키 생성
    cache_key = req.cache_key or f"embed:{hashlib.md5(req.text.encode()).hexdigest()}"

    # 캐시 확인
    try:
        cached = await cache.get(cache_key)
        if cached is not None:
            background_tasks.add_task(update_cache_stats, True)
            return EmbedResponse(
                vector=cached,
                cached=True,
                dimension=_dim,
                processing_time_ms=(time.time() - start_time) * 1000
            )
    except Exception as e:
        logger.warning(f"캐시 조회 실패: {e}")

    # 캐시 미스 - 임베딩 생성
    background_tasks.add_task(update_cache_stats, False)

    loop = asyncio.get_running_loop()
    try:
        # Thread pool에서 실행
        vec = await loop.run_in_executor(_executor, embed_sentences, [req.text])
        arr = vec[0].tolist()

        # 벡터 검증
        if len(arr) != _dim:
            raise ValueError(f"생성된 벡터 차원 오류: {len(arr)} != {_dim}")

        # 캐시 저장 (백그라운드)
        background_tasks.add_task(
            cache.set, cache_key, arr, s.cache_ttl_sec
        )

        return EmbedResponse(
            vector=arr,
            cached=False,
            dimension=_dim,
            processing_time_ms=(time.time() - start_time) * 1000
        )

    except Exception as e:
        logger.error(f"임베딩 생성 실패: {e}")
        raise HTTPException(503, f"임베딩 생성 실패: {str(e)}")

@app.post("/batch-embed", response_model=BatchEmbedResponse)
@cb
async def batch_embed(req: BatchEmbedRequest, background_tasks: BackgroundTasks):
    """배치 임베딩 생성"""
    start_time = time.time()
    batch_size = len(req.texts)

    # 캐시 키 생성
    cache_keys = []
    if req.cache_keys and len(req.cache_keys) == batch_size:
        cache_keys = req.cache_keys
    else:
        cache_keys = [
            f"embed:{hashlib.md5(text.encode()).hexdigest()}"
            for text in req.texts
        ]

    # 캐시 확인 (병렬 처리)
    vectors = [None] * batch_size
    cached_count = 0

    # 비동기 캐시 조회
    cache_tasks = []
    for i, key in enumerate(cache_keys):
        cache_tasks.append(cache.get(key))

    try:
        cache_results = await asyncio.gather(*cache_tasks, return_exceptions=True)

        for i, result in enumerate(cache_results):
            if isinstance(result, Exception):
                logger.debug(f"캐시 조회 실패 [{i}]: {result}")
            elif result is not None:
                vectors[i] = result
                cached_count += 1
    except Exception as e:
        logger.warning(f"배치 캐시 조회 실패: {e}")

    # 캐시되지 않은 텍스트 처리
    texts_to_embed = []
    indices_to_embed = []

    for i, vec in enumerate(vectors):
        if vec is None:
            texts_to_embed.append(req.texts[i])
            indices_to_embed.append(i)

    # 임베딩 생성 필요한 경우
    if texts_to_embed:
        loop = asyncio.get_running_loop()
        try:
            # 배치 크기 제한
            if len(texts_to_embed) > 50:
                # 큰 배치는 청크로 나누어 처리
                chunk_size = 50
                all_embeddings = []

                for i in range(0, len(texts_to_embed), chunk_size):
                    chunk = texts_to_embed[i:i + chunk_size]
                    chunk_embeddings = await loop.run_in_executor(
                        _executor, embed_sentences, chunk
                    )
                    all_embeddings.extend(chunk_embeddings)

                new_vecs = all_embeddings
            else:
                new_vecs = await loop.run_in_executor(
                    _executor, embed_sentences, texts_to_embed
                )

            # 결과 병합 및 캐싱
            for idx, vec in zip(indices_to_embed, new_vecs):
                vec_list = vec.tolist()
                vectors[idx] = vec_list

                # 백그라운드 캐싱
                background_tasks.add_task(
                    cache.set, cache_keys[idx], vec_list, s.cache_ttl_sec
                )

        except Exception as e:
            logger.error(f"배치 임베딩 생성 실패: {e}")
            raise HTTPException(503, f"배치 임베딩 생성 실패: {str(e)}")

    # 통계 업데이트
    background_tasks.add_task(update_cache_stats, cached_count > 0)

    return BatchEmbedResponse(
        vectors=vectors,
        cached_count=cached_count,
        dimension=_dim,
        processing_time_ms=(time.time() - start_time) * 1000,
        batch_size=batch_size
    )

@app.post("/similarity", response_model=SimilarityResponse)
async def calculate_similarity(req: SimilarityRequest):
    """두 벡터 간 유사도 계산"""
    try:
        vec1 = np.array(req.vector1)
        vec2 = np.array(req.vector2)

        # 정규화
        vec1_norm = normalize_vector(vec1)
        vec2_norm = normalize_vector(vec2)

        # 코사인 유사도
        cosine_sim = float(np.dot(vec1_norm, vec2_norm))

        # 유클리드 거리
        euclidean_dist = float(np.linalg.norm(vec1 - vec2))

        # 내적
        dot_product = float(np.dot(vec1, vec2))

        return SimilarityResponse(
            cosine_similarity=cosine_sim,
            euclidean_distance=euclidean_dist,
            dot_product=dot_product
        )

    except Exception as e:
        raise HTTPException(400, f"유사도 계산 실패: {str(e)}")

@app.get("/healthz", response_model=HealthResponse)
async def healthz():
    """헬스 체크 엔드포인트"""
    # 모델 상태 확인
    model_loaded = _executor is not None
    cache_healthy = True

    try:
        # 캐시 연결 확인
        await asyncio.wait_for(cache.ping(), timeout=1.0)
    except:
        cache_healthy = False

    status = "healthy" if model_loaded and cache_healthy else "unhealthy"
    uptime = time.time() - _start_time

    response = HealthResponse(
        status=status,
        model_loaded=model_loaded,
        cache_healthy=cache_healthy,
        dimension=_dim,
        model_name=s.model_name,
        uptime_seconds=uptime
    )

    # 상태가 unhealthy면 503 반환
    if status == "unhealthy":
        return JSONResponse(
            status_code=503,
            content=response.dict()
        )

    return response

@app.get("/stats")
async def get_stats():
    """서비스 통계"""
    try:
        # 캐시 통계
        hits = await cache.get("stats:hits") or 0
        misses = await cache.get("stats:misses") or 0
        total = hits + misses
        hit_rate = (hits / total * 100) if total > 0 else 0

        # Circuit Breaker 상태
        cb_state = {
            "state": cb.current_state.name,
            "failure_count": cb.failure_count,
            "success_count": cb.success_count,
            "last_failure": cb.last_failure.isoformat() if cb.last_failure else None
        }

        # 시스템 정보
        import psutil
        process = psutil.Process()
        system_info = {
            "cpu_percent": process.cpu_percent(),
            "memory_mb": process.memory_info().rss / 1024 / 1024,
            "threads": process.num_threads()
        }

        return {
            "cache": {
                "hits": hits,
                "misses": misses,
                "total": total,
                "hit_rate": f"{hit_rate:.1f}%"
            },
            "circuit_breaker": cb_state,
            "system": system_info,
            "uptime_seconds": time.time() - _start_time,
            "config": {
                "model": s.model_name,
                "dimension": _dim,
                "cache_ttl": s.cache_ttl_sec,
                "pool_size": s.pool_size
            }
        }

    except Exception as e:
        logger.error(f"통계 조회 실패: {e}")
        return {"error": str(e)}

@app.delete("/cache/clear")
async def clear_cache(pattern: str = "embed:*", api_key: Optional[str] = None):
    """캐시 삭제 (관리용)"""
    # 간단한 API 키 인증
    if api_key != "admin-secret-key":  # 실제로는 환경변수로 관리
        raise HTTPException(403, "Unauthorized")

    try:
        # 패턴에 맞는 키 조회
        keys = []
        async for key in cache.scan_iter(pattern):
            keys.append(key)

        # 키 삭제
        if keys:
            await cache.delete(*keys)

        return {
            "deleted": len(keys),
            "pattern": pattern
        }

    except Exception as e:
        raise HTTPException(500, f"캐시 삭제 실패: {str(e)}")

@app.post("/warmup")
async def warmup(texts: List[str] = None):
    """모델 워밍업 (선택적 텍스트 제공)"""
    if not texts:
        texts = [
            "상품 추천",
            "이 제품과 비슷한",
            "가격대가 비슷한",
            "인기 있는 상품"
        ]

    try:
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(_executor, embed_sentences, texts)
        return {"status": "warmed up", "texts_count": len(texts)}
    except Exception as e:
        raise HTTPException(500, f"워밍업 실패: {str(e)}")

# ────────────────────────── 메트릭 ─────────────────────────────
# Prometheus 메트릭 노출
instrumentator = Instrumentator()
instrumentator.instrument(app).expose(app, include_in_schema=False)

# 커스텀 메트릭 추가
from prometheus_client import Counter, Histogram, Gauge

# 메트릭 정의
embedding_requests = Counter(
    'ml_embedding_requests_total',
    'Total embedding requests',
    ['method', 'cached']
)

embedding_duration = Histogram(
    'ml_embedding_duration_seconds',
    'Embedding generation duration',
    ['method']
)

cache_hit_rate = Gauge(
    'ml_cache_hit_rate',
    'Cache hit rate percentage'
)

# 에러 핸들러
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    logger.error(f"Unhandled exception: {exc}", exc_info=True)
    return JSONResponse(
        status_code=500,
        content={
            "error": "Internal server error",
            "message": str(exc) if s.log_level == "DEBUG" else "An error occurred"
        }
    )