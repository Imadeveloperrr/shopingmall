"""
ML 서비스 코어 모델 모듈
Sentence Transformers를 사용한 텍스트 임베딩 생성
"""
import os
import logging
from typing import List, Optional
from concurrent.futures import ThreadPoolExecutor
import numpy as np
import torch
from sentence_transformers import SentenceTransformer

from .config import get_settings

# 로깅 설정
logger = logging.getLogger(__name__)

# 설정 로드
settings = get_settings()

# 글로벌 변수
_model: Optional[SentenceTransformer] = None
_executor: Optional[ThreadPoolExecutor] = None
_device: str = "cpu"
_dim: int = 384  # all-MiniLM-L6-v2의 차원

def initialize_model():
    """모델 초기화"""
    global _model, _executor, _device, _dim

    try:
        # CUDA 사용 가능 확인
        if torch.cuda.is_available():
            _device = "cuda"
            logger.info("CUDA 사용 가능 - GPU 모드로 실행")
        else:
            logger.info("CPU 모드로 실행")

        # 모델 로드
        logger.info(f"모델 로드 중: {settings.model_name}")
        _model = SentenceTransformer(
            settings.model_name,
            device=_device,
            cache_folder="./model_cache"
        )

        # 모델 차원 확인
        test_embedding = _model.encode(["test"], convert_to_numpy=True)
        _dim = test_embedding.shape[1]
        logger.info(f"모델 로드 완료. 임베딩 차원: {_dim}")

        # Thread pool 초기화
        _executor = ThreadPoolExecutor(
            max_workers=settings.pool_size,
            thread_name_prefix="embedding-"
        )
        logger.info(f"Thread pool 초기화 완료: {settings.pool_size} workers")

        # 모델 최적화 설정
        if hasattr(_model, 'eval'):
            _model.eval()  # 평가 모드로 설정

        return True

    except Exception as e:
        logger.error(f"모델 초기화 실패: {e}")
        raise

def embed_sentences(texts: List[str]) -> np.ndarray:
    """텍스트 리스트를 임베딩 벡터로 변환"""
    if _model is None:
        raise RuntimeError("모델이 초기화되지 않았습니다")

    if not texts:
        return np.array([])

    try:
        # 텍스트 전처리
        processed_texts = []
        for text in texts:
            if text and isinstance(text, str):
                # 텍스트 정리 (너무 긴 텍스트 자르기)
                clean_text = text.strip()[:512]  # 대부분의 모델은 512 토큰 제한
                processed_texts.append(clean_text)
            else:
                processed_texts.append("")

        # 배치 크기 제어
        batch_size = min(32, len(processed_texts))  # GPU 메모리 고려

        # 임베딩 생성
        embeddings = _model.encode(
            processed_texts,
            batch_size=batch_size,
            show_progress_bar=False,
            convert_to_numpy=True,
            normalize_embeddings=True  # 코사인 유사도 계산을 위해 정규화
        )

        # 차원 검증
        if embeddings.shape[1] != _dim:
            logger.warning(f"임베딩 차원 불일치: expected={_dim}, actual={embeddings.shape[1]}")

        return embeddings

    except Exception as e:
        logger.error(f"임베딩 생성 실패: {e}")
        raise

def get_model_info() -> dict:
    """모델 정보 반환"""
    if _model is None:
        return {
            "loaded": False,
            "name": settings.model_name,
            "dimension": _dim
        }

    return {
        "loaded": True,
        "name": settings.model_name,
        "dimension": _dim,
        "device": _device,
        "max_seq_length": getattr(_model, 'max_seq_length', 512)
    }

def cleanup():
    """리소스 정리"""
    global _executor

    if _executor:
        logger.info("Thread pool 종료 중...")
        _executor.shutdown(wait=True)
        _executor = None

# 모듈 로드 시 자동 초기화
try:
    initialize_model()
except Exception as e:
    logger.error(f"모델 자동 초기화 실패: {e}")
    # 서비스는 계속 시작하되, 첫 요청 시 다시 시도

# 모듈 종료 시 정리
import atexit
atexit.register(cleanup)