import redis
from django.conf import settings
import hashlib

_redis = None

def get_redis():
    global _redis
    if _redis is None:
        _redis = redis.Redis(
            host=settings.REDIS_HOST,
            port=settings.REDIS_PORT,
            password=settings.REDIS_PASSWORD,
            decode_responses=True,
            db=0
        )
    return _redis

def hash(input: str) -> str:
    return hashlib.sha256(input).hexdigest()