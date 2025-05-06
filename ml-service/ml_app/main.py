import datetime

from fastapi import FastAPI, HTTPException
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
    version="1.0.0",
    docs_url="/docs",
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

class EmbedResponse(BaseModel):
    vector: list[float] = Field(..., description=f"임베딩 벡터({_dim}차원)")

# ───────────────────────── APIs ─────────────────────────────────
@app.post("/embed", response_model=EmbedResponse)
@cb
async def embed(req: EmbedRequest):
    key = f"embed:{req.text}"
    if (cached := await cache.get(key)) is not None:
        return {"vector": cached}

    loop = asyncio.get_running_loop()
    try:
        # thread pool 오프로딩
        vec = await loop.run_in_executor(_executor, embed_sentences, [req.text])
        arr = vec[0].tolist()
        await cache.set(key, arr, ttl=s.cache_ttl_sec)
        return {"vector": arr}
    except Exception as e:
        raise HTTPException(503, f"Inference error: {e}")

@app.get("/healthz")
async def healthz():
    return {"status": "ok"}

# ────────────────────────── Metrics ─────────────────────────────
Instrumentator().instrument(app).expose(app, include_in_schema=False)