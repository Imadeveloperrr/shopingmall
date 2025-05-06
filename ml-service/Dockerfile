FROM python:3.11-slim

ENV PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1

# 필수 패키지
RUN apt-get update && apt-get install -y --no-install-recommends \
      git gcc g++ curl && \
    pip install --upgrade pip

COPY requirements.txt /tmp/
RUN pip install -r /tmp/requirements.txt

COPY ml_app /app/ml_app
WORKDIR /app

EXPOSE 8000
HEALTHCHECK CMD curl --fail http://localhost:8000/healthz || exit 1

CMD ["uvicorn", "ml_app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "4"]
