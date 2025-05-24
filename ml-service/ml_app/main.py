import datetime
import hashlib
from typing import List, Optional

from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
import numpy as np
import asyncio
from aiobreaker import CircuitBreaker, CircuitBreakerListener
from prometheus_fastapi_instrumentator import Instrumentator

from .core.config import get_settings
from .core.model import embed_sentences, _executor, _dim
from .core.cache import cache

s = get_settings()
app = FastAPI(
    title="Embedding Service",
    version="2.0.0",
    docs_url="/docs",
)

# CORS 설정 추가
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# ──────────────────────── Circuit Breaker ────────────────────────
class CBListener(CircuitBreakerListener):
    async def state_change(self, cb, old_state, new_state):
        app.logger.warning(f"[CB] {old_state.name} → {new_state.name}")

cb = CircuitBreaker(
    fail_max=int(s.cb_failure_rate * 100),
    timeout_duration=datetime.timedelta(seconds=s.cb_reset_timeout),
    listeners=[CBListener()],
)

# ──────────────────────────── DTOs ───────────────────────────────
class EmbedRequest(BaseModel):
    text: str = Field(..., min_length=1, max_length=2048)
    cache_key: Optional[str] = Field(None, description="커스텀 캐시 키")

class BatchEmbedRequest(BaseModel):
    texts: List[str] = Field(..., min_items=1, max_items=100)
    cache_keys: Optional[List[str]] = None

class EmbedResponse(BaseModel):
    vector: list[float] = Field(..., description=f"임베딩 벡터({_dim}차원)")
    cached: bool = Field(False, description="캐시에서 조회됨")
    dimension: int = Field(_dim, description="벡터 차원")

class BatchEmbedResponse(BaseModel):
    vectors: List[list[float]]
    cached_count: int
    dimension: int = Field(_dim)

class SimilarityRequest(BaseModel):
    vector1: list[float]
    vector2: list[float]

class SimilarityResponse(BaseModel):
    cosine_similarity: float
    euclidean_distance: float

# ───────────────────────── APIs ─────────────────────────────────
@app.post("/embed", response_model=EmbedResponse)
@cb
async def embed(req: EmbedRequest):
    # 캐시 키 생성
    cache_key = req.cache_key or f"embed:{hashlib.md5(req.text.encode()).hexdigest()}"

    # 캐시 확인
    cached = await cache.get(cache_key)
    if cached is not None:
        return {"vector": cached, "cached": True, "dimension": _dim}

    loop = asyncio.get_running_loop()
    try:
        # thread pool 오프로딩
        vec = await loop.run_in_executor(_executor, embed_sentences, [req.text])
        arr = vec[0].tolist()

        # 캐시 저장
        await cache.set(cache_key, arr, ttl=s.cache_ttl_sec)

        return {"vector": arr, "cached": False, "dimension": _dim}
    except Exception as e:
        raise HTTPException(503, f"Inference error: {e}")

@app.post("/batch-embed", response_model=BatchEmbedResponse)
@cb
async def batch_embed(req: BatchEmbedRequest, background_tasks: BackgroundTasks):
    """배치 임베딩 생성"""
    vectors = []
    cached_count = 0

    # 캐시 확인
    for i, text in enumerate(req.texts):
        cache_key = (req.cache_keys[i] if req.cache_keys and i < len(req.cache_keys)
                     else f"embed:{hashlib.md5(text.encode()).hexdigest()}")

        cached = await cache.get(cache_key)
        if cached is not None:
            vectors.append(cached)
            cached_count += 1
        else:
            vectors.append(None)

    # 캐시되지 않은 텍스트만 임베딩 생성
    texts_to_embed = []
    indices_to_embed = []

    for i, vec in enumerate(vectors):
        if vec is None:
            texts_to_embed.append(req.texts[i])
            indices_to_embed.append(i)

    if texts_to_embed:
        loop = asyncio.get_running_loop()
        try:
            # 배치 임베딩 생성
            new_vecs = await loop.run_in_executor(_executor, embed_sentences, texts_to_embed)

            # 결과 병합 및 캐싱
            for idx, vec in zip(indices_to_embed, new_vecs):
                vec_list = vec.tolist()
                vectors[idx] = vec_list

                # 백그라운드에서 캐싱
                cache_key = (req.cache_keys[idx] if req.cache_keys and idx < len(req.cache_keys)
                             else f"embed:{hashlib.md5(req.texts[idx].encode()).hexdigest()}")
                background_tasks.add_task(cache.set, cache_key, vec_list, s.cache_ttl_sec)

        except Exception as e:
            raise HTTPException(503, f"Batch inference error: {e}")

    return {
        "vectors": vectors,
        "cached_count": cached_count,
        "dimension": _dim
    }

@app.post("/similarity", response_model=SimilarityResponse)
async def calculate_similarity(req: SimilarityRequest):
    """두 벡터 간 유사도 계산"""
    try:
        vec1 = np.array(req.vector1)
        vec2 = np.array(req.vector2)

        # 차원 확인
        if len(vec1) != len(vec2):
            raise ValueError("벡터 차원이 일치하지 않습니다")

        # 코사인 유사도
        cosine_sim = np.dot(vec1, vec2) / (np.linalg.norm(vec1) * np.linalg.norm(vec2))

        # 유클리드 거리
        euclidean_dist = np.linalg.norm(vec1 - vec2)

        return {
            "cosine_similarity": float(cosine_sim),
            "euclidean_distance": float(euclidean_dist)
        }
    except Exception as e:
        raise HTTPException(400, f"Similarity calculation error: {e}")

@app.get("/healthz")
async def healthz():
    """헬스 체크 엔드포인트"""
    # 모델 상태 확인
    model_loaded = _executor is not None
    cache_healthy = True

    try:
        # 캐시 연결 확인
        await cache.ping()
    except:
        cache_healthy = False

    status = "healthy" if model_loaded and cache_healthy else "unhealthy"

    return {
        "status": status,
        "model_loaded": model_loaded,
        "cache_healthy": cache_healthy,
        "dimension": _dim,
        "model_name": s.model_name
    }

@app.get("/stats")
async def get_stats():
    """서비스 통계"""
    try:
        # 캐시 통계
        cache_stats = {
            "hits": await cache.get("stats:hits") or 0,
            "misses": await cache.get("stats:misses") or 0,
            "keys": await cache.exists("embed:*")
        }

        # Circuit Breaker 상태
        cb_state = {
            "state": cb.current_state.name,
            "failure_count": cb.failure_count,
            "success_count": cb.success_count,
            "last_failure": cb.last_failure
        }

        return {
            "cache": cache_stats,
            "circuit_breaker": cb_state,
            "thread_pool": {
                "workers": s.pool_size
            }
        }
    except Exception as e:
        return {"error": str(e)}

@app.delete("/cache/clear")
async def clear_cache(pattern: str = "embed:*"):
    """캐시 삭제 (관리용)"""
    try:
        deleted = await cache.clear(namespace=pattern)
        return {"deleted": deleted}
    except Exception as e:
        raise HTTPException(500, f"Cache clear error: {e}")

# ────────────────────────── Metrics ─────────────────────────────
Instrumentator().instrument(app).expose(app, include_in_schema=False)

# ────────────────────────── Startup ─────────────────────────────
@app.on_event("startup")
async def startup_event():
    """애플리케이션 시작 시 워밍업"""
    try:
        # 모델 워밍업 (첫 추론은 느림)
        test_text = "warm up"
        loop = asyncio.get_running_loop()
        await loop.run_in_executor(_executor, embed_sentences, [test_text])
        print(f"✅ Model warmed up. Dimension: {_dim}")
    except Exception as e:
        print(f"❌ Warmup failed: {e}")