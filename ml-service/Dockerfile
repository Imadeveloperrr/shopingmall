# Multi-stage build for optimized image
FROM python:3.11-slim as builder

# Build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    gcc \
    g++ \
    curl \
    git \
    && rm -rf /var/lib/apt/lists/*

# Install Python dependencies
WORKDIR /tmp
COPY requirements.txt .
RUN pip install --user --no-cache-dir --upgrade pip && \
    pip install --user --no-cache-dir -r requirements.txt

# Download model during build (cache it)
RUN python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('sentence-transformers/all-MiniLM-L6-v2', cache_folder='/tmp/model_cache')"

# Runtime stage
FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1

# Install runtime dependencies only
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Create non-root user
RUN useradd -m -u 1000 mluser

# Copy installed packages from builder
COPY --from=builder /root/.local /home/mluser/.local
COPY --from=builder /tmp/model_cache /app/model_cache

# Copy application
WORKDIR /app
COPY ml_app /app/ml_app

# Set permissions
RUN chown -R mluser:mluser /app

# Switch to non-root user
USER mluser

# Add user's local bin to PATH
ENV PATH=/home/mluser/.local/bin:$PATH

# Expose port
EXPOSE 8000

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8000/healthz || exit 1

# Run with optimal settings
CMD ["uvicorn", "ml_app.main:app", \
    "--host", "0.0.0.0", \
    "--port", "8000", \
    "--workers", "4", \
    "--loop", "uvloop", \
    "--access-log", \
    "--log-level", "info"]