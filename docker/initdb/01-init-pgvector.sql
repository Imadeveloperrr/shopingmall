-- pgvector 확장 설치
CREATE EXTENSION IF NOT EXISTS vector;

-- 설치 확인
SELECT 'pgvector 확장이 설치되었습니다: ' || extversion AS result
FROM pg_extension
WHERE extname = 'vector';
