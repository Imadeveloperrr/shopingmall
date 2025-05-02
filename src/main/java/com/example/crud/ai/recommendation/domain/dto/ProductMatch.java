package com.example.crud.ai.recommendation.domain.dto;

/**
 * 벡터 DB 유사도 검색 결과를 보관하는 레코드.
 *  - id      : 상품 PK
 *  - score   : 0.0 ~ 1.0 (1.0에 가까울수록 유사)
 *  - product : 엔티티를 곧바로 불러오면 N+1 위험 → 선택적 Lazy 로딩
 */
public record ProductMatch(
        Long id,
        String name,
        double score
        // Product product     // 필요 시 null 로 두고 id 로만 꺼내 써도 됨
) {}