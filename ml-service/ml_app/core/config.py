from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    # 모델 설정
    model_name: str = "sentence-transformers/all-MiniLM-L6-v2"
    pool_size: int = 8

    # Redis 설정 (환경변수에서 우선 로드, 기본값은 DB 1)
    redis_url: str = "redis://redis:6379/1"

    # Circuit Breaker 설정
    cb_failure_rate: float = 0.5
    cb_reset_timeout: int = 30

    # 캐시 설정
    cache_ttl_sec: int = 7200
    cache_max_keys: int = 50_000

    # 성능 설정
    max_batch_size: int = 100
    request_timeout_sec: int = 10

    # 모니터링
    enable_metrics: bool = True
    log_level: str = "INFO"

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        protected_namespaces=("settings_",)
    )

@lru_cache
def get_settings() -> Settings:
    return Settings()