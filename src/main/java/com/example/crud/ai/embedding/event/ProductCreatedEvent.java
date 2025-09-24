package com.example.crud.ai.embedding.event;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 상품 생성 이벤트
 */
@Getter
@AllArgsConstructor
public class ProductCreatedEvent {
    private final Long productId;
}