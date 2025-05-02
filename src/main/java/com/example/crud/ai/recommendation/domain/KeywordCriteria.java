package com.example.crud.ai.recommendation.domain;

import com.example.crud.ai.recommendation.domain.dto.Preference;
import lombok.*;

import java.util.Set;

/**
 * 상품 검색 시 사용할 키워드 및 필터링 조건을 캡슐화한 객체.
 * 불변(immutable) 설계로, 생성 후 상태 변경 불가.
 */

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeywordCriteria {
    private com.example.crud.enums.Category category;
    private Set<String> includeKeywords;
    private String season;

    public static KeywordCriteria from(Preference p) {
        java.util.Set<String> kw = new java.util.HashSet<>();
        if (p.getStyle() != null) kw.add(p.getStyle());
        if (p.getColor() != null) kw.add(p.getColor());
        return KeywordCriteria.builder()
                .category(com.example.crud.enums.Category.fromGroupName(p.getCategory()))
                .includeKeywords(kw)
                .season(p.getSeason())
                .build();
    }
}
