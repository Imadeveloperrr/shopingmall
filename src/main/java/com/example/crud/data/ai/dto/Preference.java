package com.example.crud.data.ai.dto;

import lombok.Data;

@Data
public class Preference {
    private String description; // 추가
    private String category;
    private String style;
    private String color;
    private String size;
}
