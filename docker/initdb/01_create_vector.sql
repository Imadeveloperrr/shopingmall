CREATE EXTENSION IF NOT EXISTS vector;

CREATE INDEX IF NOT EXISTS idx_product_desc_vec
    ON  product
USING ivfflat (description_vector vector_cosine_ops)
WITH (lists = 100);