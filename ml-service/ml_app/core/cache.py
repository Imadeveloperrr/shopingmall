"""
ML 서비스 캐시 모듈
Redis를 사용한 임베딩 캐싱
"""
import json
import logging
import pickle
from typing import Any, Optional, AsyncIterator
import aioredis
from aioredis import Redis
import asyncio

from .config import get_settings

# 로깅 설정
logger = logging.getLogger(__name__)

# 설정 로드
settings = get_settings()

class CacheManager:
    """Redis 캐시 매니저"""

    def __init__(self):
        self._redis: Optional[Redis] = None
        self._connected = False

    async def connect(self):
        """Redis 연결"""
        if self._connected:
            return

        try:
            self._redis = await aioredis.from_url(
                settings.redis_url,
                encoding="utf-8",
                decode_responses=False,  # 바이너리 데이터 처리를 위해
                max_connections=10,
                health_check_interval=30
            )

            # 연결 테스트
            await self._redis.ping()
            self._connected = True
            logger.info("Redis 캐시 연결 성공")

        except Exception as e:
            logger.error(f"Redis 연결 실패: {e}")
            self._connected = False
            raise

    async def disconnect(self):
        """Redis 연결 해제"""
        if self._redis:
            await self._redis.close()
            self._connected = False
            logger.info("Redis 연결 해제")

    async def get(self, key: str) -> Optional[Any]:
        """캐시에서 값 조회"""
        if not self._connected:
            await self.connect()

        try:
            value = await self._redis.get(key)
            if value is None:
                return None

            # JSON으로 디코딩 시도
            try:
                return json.loads(value)
            except:
                # 실패하면 pickle로 시도
                try:
                    return pickle.loads(value)
                except:
                    return value.decode('utf-8') if isinstance(value, bytes) else value

        except Exception as e:
            logger.warning(f"캐시 조회 실패 [{key}]: {e}")
            return None

    async def set(self, key: str, value: Any, ttl: Optional[int] = None) -> bool:
        """캐시에 값 저장"""
        if not self._connected:
            await self.connect()

        try:
            # 값 직렬화
            if isinstance(value, (list, dict)):
                serialized = json.dumps(value)
            elif isinstance(value, (str, int, float)):
                serialized = str(value)
            else:
                serialized = pickle.dumps(value)

            # TTL 설정
            if ttl is None:
                ttl = settings.cache_ttl_sec

            # 저장
            await self._redis.setex(key, ttl, serialized)
            return True

        except Exception as e:
            logger.warning(f"캐시 저장 실패 [{key}]: {e}")
            return False

    async def delete(self, *keys: str) -> int:
        """캐시에서 키 삭제"""
        if not self._connected:
            await self.connect()

        try:
            if keys:
                return await self._redis.delete(*keys)
            return 0
        except Exception as e:
            logger.warning(f"캐시 삭제 실패: {e}")
            return 0

    async def exists(self, key: str) -> bool:
        """키 존재 여부 확인"""
        if not self._connected:
            await self.connect()

        try:
            return await self._redis.exists(key) > 0
        except Exception as e:
            logger.warning(f"캐시 존재 확인 실패 [{key}]: {e}")
            return False

    async def incr(self, key: str, amount: int = 1) -> Optional[int]:
        """카운터 증가"""
        if not self._connected:
            await self.connect()

        try:
            return await self._redis.incrby(key, amount)
        except Exception as e:
            logger.warning(f"카운터 증가 실패 [{key}]: {e}")
            return None

    async def hincr(self, key: str, field: str, amount: int = 1) -> Optional[int]:
        """해시 필드 카운터 증가"""
        if not self._connected:
            await self.connect()

        try:
            return await self._redis.hincrby(key, field, amount)
        except Exception as e:
            logger.warning(f"해시 카운터 증가 실패 [{key}:{field}]: {e}")
            return None

    async def expire(self, key: str, seconds: int) -> bool:
        """키 만료 시간 설정"""
        if not self._connected:
            await self.connect()

        try:
            return await self._redis.expire(key, seconds)
        except Exception as e:
            logger.warning(f"만료 시간 설정 실패 [{key}]: {e}")
            return False

    async def scan_iter(self, pattern: str) -> AsyncIterator[str]:
        """패턴에 맞는 키 검색"""
        if not self._connected:
            await self.connect()

        try:
            async for key in self._redis.scan_iter(pattern):
                yield key.decode('utf-8') if isinstance(key, bytes) else key
        except Exception as e:
            logger.warning(f"키 검색 실패 [{pattern}]: {e}")

    async def ping(self) -> bool:
        """연결 상태 확인"""
        if not self._connected:
            await self.connect()

        try:
            await self._redis.ping()
            return True
        except Exception as e:
            logger.warning(f"Ping 실패: {e}")
            self._connected = False
            return False

    async def mget(self, keys: list) -> list:
        """여러 키 한번에 조회"""
        if not self._connected:
            await self.connect()

        try:
            values = await self._redis.mget(keys)
            results = []

            for value in values:
                if value is None:
                    results.append(None)
                else:
                    try:
                        results.append(json.loads(value))
                    except:
                        results.append(value.decode('utf-8') if isinstance(value, bytes) else value)

            return results

        except Exception as e:
            logger.warning(f"다중 조회 실패: {e}")
            return [None] * len(keys)

    async def get_stats(self) -> dict:
        """캐시 통계 조회"""
        if not self._connected:
            await self.connect()

        try:
            info = await self._redis.info()

            return {
                "connected_clients": info.get("connected_clients", 0),
                "used_memory_human": info.get("used_memory_human", "0"),
                "total_commands_processed": info.get("total_commands_processed", 0),
                "keyspace_hits": info.get("keyspace_hits", 0),
                "keyspace_misses": info.get("keyspace_misses", 0),
                "hit_rate": self._calculate_hit_rate(
                    info.get("keyspace_hits", 0),
                    info.get("keyspace_misses", 0)
                )
            }

        except Exception as e:
            logger.warning(f"통계 조회 실패: {e}")
            return {}

    def _calculate_hit_rate(self, hits: int, misses: int) -> float:
        """히트율 계산"""
        total = hits + misses
        if total == 0:
            return 0.0
        return round(hits / total * 100, 2)

    async def clear(self, namespace: str = "*") -> int:
        """네임스페이스 기준으로 캐시 클리어"""
        if not self._connected:
            await self.connect()

        try:
            keys = []
            async for key in self.scan_iter(namespace):
                keys.append(key)

            if keys:
                return await self.delete(*keys)
            return 0

        except Exception as e:
            logger.warning(f"캐시 클리어 실패 [{namespace}]: {e}")
            return 0

# 싱글톤 인스턴스
cache = CacheManager()

# 헬퍼 함수들
async def warmup_cache():
    """캐시 워밍업"""
    try:
        await cache.connect()

        # 자주 사용되는 키 미리 로드
        common_phrases = [
            "상품 추천",
            "이 제품과 비슷한",
            "가격대가 비슷한",
            "인기 있는 상품",
            "최신 상품",
            "할인 상품"
        ]

        for phrase in common_phrases:
            key = f"embed:{hash(phrase)}"
            if not await cache.exists(key):
                logger.debug(f"캐시 워밍업 필요: {phrase}")

        logger.info("캐시 워밍업 완료")

    except Exception as e:
        logger.error(f"캐시 워밍업 실패: {e}")

# 모듈 정리
async def cleanup():
    """리소스 정리"""
    await cache.disconnect()