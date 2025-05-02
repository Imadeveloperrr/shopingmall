package com.example.crud.ai.embedding.domain.repository;

import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.pgvector.PGvector;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.SQLException;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class ProductVectorRepository {

    private final NamedParameterJdbcTemplate template;

    /** 입력 벡터와 가장 유사한 상품 N개 조회 (cosine distance) */
    public List<ProductMatch> findTopN(float[] query, int topN) {
        String sql = """
            SELECT id, name,
                   1 - (embedding <#> :q) AS score
            FROM product_vectors
            ORDER BY embedding <#> :q
            LIMIT :top
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", toPGobject(query))
                .addValue("top", topN);

        return template.query(sql, params, (rs, i) -> new ProductMatch(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDouble("score")
        ));
    }

    /** float[] → PGobject(vector) */
    private PGobject toPGobject(float[] vec) {
        try {
            PGvector v = new PGvector(vec);
            PGobject obj = new PGobject();
            obj.setType("vector");
            obj.setValue(v.toString());   // "[0.12,0.34,...]"
            return obj;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to convert vector", e);
        }
    }
}
