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

    /**
     * 입력 벡터와 가장 유사한 상품 N개 조회 (cosine distance)
     * product 테이블의 description_vector 컬럼 사용
     */
    public List<ProductMatch> findTopN(float[] query, int topN) {
        String sql = """
            SELECT p.number as id, p.name,
                   1 - (p.description_vector <#> :q) AS score
            FROM product p
            WHERE p.description_vector IS NOT NULL
            ORDER BY p.description_vector <#> :q
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

    /**
     * 카테고리별 유사 상품 검색
     */
    public List<ProductMatch> findTopNByCategory(float[] query, String category, int topN) {
        String sql = """
            SELECT p.number as id, p.name,
                   1 - (p.description_vector <#> :q) AS score
            FROM product p
            WHERE p.description_vector IS NOT NULL
              AND p.category = :category
            ORDER BY p.description_vector <#> :q
            LIMIT :top
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", toPGobject(query))
                .addValue("category", category)
                .addValue("top", topN);

        return template.query(sql, params, (rs, i) -> new ProductMatch(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getDouble("score")
        ));
    }

    /**
     * 가격 범위 내 유사 상품 검색
     */
    public List<ProductMatch> findTopNByPriceRange(float[] query, int minPrice, int maxPrice, int topN) {
        String sql = """
            SELECT p.number as id, p.name,
                   1 - (p.description_vector <#> :q) AS score
            FROM product p
            WHERE p.description_vector IS NOT NULL
              AND p.price BETWEEN :minPrice AND :maxPrice
            ORDER BY p.description_vector <#> :q
            LIMIT :top
            """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("q", toPGobject(query))
                .addValue("minPrice", minPrice)
                .addValue("maxPrice", maxPrice)
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
            obj.setValue(v.toString());
            return obj;
        } catch (SQLException e) {
            throw new RuntimeException("Failed to convert vector", e);
        }
    }
}