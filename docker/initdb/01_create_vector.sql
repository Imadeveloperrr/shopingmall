CREATE EXTENSION IF NOT EXISTS vector;

DO $$
BEGIN
    -- product 테이블이 존재하는지 확인
    IF EXISTS (
        SELECT FROM information_schema.tables
        WHERE table_name = 'product'
    ) THEN
        -- 테이블이 존재하면 인덱스 생성
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_product_desc_vec
                ON product
                USING ivfflat (description_vector vector_cosine_ops)
                WITH (lists = 100)';
    END IF;
END $$;