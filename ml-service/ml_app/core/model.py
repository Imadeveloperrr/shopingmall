import concurrent.futures as cf
from sentence_transformers import SentenceTransformer
from typing import List
import numpy as np

from .config import get_settings

_settings = get_settings()

# ▶ 모델 메모리 로드 (Lazy Singleton)
_model = SentenceTransformer(_settings.model_name)
_dim = _model.get_sentence_embedding_dimension()

# ▶ CPU-bound 예측용 Thread Pool
_executor = cf.ThreadPoolExecutor(max_workers=_settings.pool_size)

def embed_sentences(sentences: List[str]) -> List[np.ndarray]:
    """동기 함수 – 스레드풀 내부에서 실행"""
    return _model.encode(sentences, show_progress_bar=False, convert_to_numpy=True)
