from functools import lru_cache
from pydantic_settings import BaseSettings, SettingsConfigDict

class Settings(BaseSettings):
    model_name: str = "sentence-transformers/all-MiniLM-L6-v2"
    pool_size: int = 8
    redis_url: str = "redis://redis:6379/0"
    cb_failure_rate: float = 0.5
    cb_reset_timeout: int = 30
    cache_ttl_sec: int = 7200
    cache_max_keys: int = 50_000
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        protected_namespaces=("settings_",)   # 경고 제거
    )

@lru_cache
def get_settings() -> Settings:
    return Settings()
