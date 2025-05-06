from aiocache import caches, Cache
from .config import get_settings

s = get_settings()
caches.set_config({
    "default": {
        "cache": "aiocache.RedisCache",
        "endpoint": s.redis_url.split("//")[1].split(":")[0],
        "port": int(s.redis_url.split(":")[-1].split("/")[0]),
        "timeout": 1,
        "serializer": {"class": "aiocache.serializers.PickleSerializer"},
        "plugins": [],
    }
})
cache: Cache = caches.get("default")
