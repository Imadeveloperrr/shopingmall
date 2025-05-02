package com.example.crud.ai.recommendation.domain.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Preference {
    private String category;
    private String style;
    private String color;
    private String size;
    private String season;
    private String originalSentence; // 사용자 원문
}
