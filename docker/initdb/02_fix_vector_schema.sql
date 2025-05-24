-- ML 서비스가 all-MiniLM-L6-v2 모델 사용 (384차원)
-- 기존 768차원에서 384차원으로 변경

-- 1. 기존 컬럼이 있다면 삭제 후 재생성 (차원 변경)
DO $
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product'
        AND column_name = 'description_vector'
    ) THEN
        -- 기존 인덱스 삭제
DROP INDEX IF EXISTS idx_product_desc_vec;
DROP INDEX IF EXISTS idx_product_with_vector;

-- 컬럼 삭제 후 재생성
ALTER TABLE product DROP COLUMN description_vector;
END IF;

    -- 384차원 벡터 컬럼 추가
ALTER TABLE product ADD COLUMN description_vector vector(384);
END $;

-- 2. 벡터 검색을 위한 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_product_desc_vec
    ON product USING ivfflat (description_vector vector_cosine_ops)
    WITH (lists = 100);

-- 3. product_vectors 테이블이 있다면 삭제 (혼란 방지)
DROP TABLE IF EXISTS product_vectors;

-- 4. 벡터 검색 성능을 위한 부분 인덱스 (벡터가 있는 상품만)
CREATE INDEX IF NOT EXISTS idx_product_with_vector
    ON product (category)
    WHERE description_vector IS NOT NULL;

-- 5. 카테고리별 벡터 인덱스 (성능 최적화)
CREATE INDEX IF NOT EXISTS idx_product_category_vec
    ON product (category, number)
    WHERE description_vector IS NOT NULL;