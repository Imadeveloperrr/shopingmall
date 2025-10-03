package com.example.crud.ai.embedding.domain;

import com.example.crud.entity.Product;
import org.springframework.stereotype.Component;

/**
 * Product Entity를 Embedding용 Text로 Extract하는 Builder
 */
@Component
public class ProductTextBuilder {

    /**
     * Product Entity를 임베딩용 텍스트로 변환
     */
    public String buildProductText(Product product) {
        if (product == null) {
            throw new IllegalArgumentException("Product가 null입니다.");
        }

        StringBuilder text = new StringBuilder();

        // 1. 상품명
        if (product.getName() != null && !product.getName().isBlank()) {
            text.append(product.getName()).append(" ");
        }

        // 2. 카테고리
        if (product.getCategory() != null) {
            text.append(product.getCategory().name()).append(" ");
        }

        // 3. 상품 설명 (원본 사용)
        if (product.getDescription() != null && !product.getDescription().isBlank()) {
            text.append(product.getDescription()).append(" ");
        }

        // 4. 브랜드
        if (product.getBrand() != null && !product.getBrand().isBlank()) {
            text.append(product.getBrand()).append(" ");
        }

        // 5. 가격대 정보
        if (product.getPrice() != null) {
            String priceRange = getPriceRange(product.getPrice());
            text.append(priceRange).append(" ");
        }

        return text.toString().trim();
    }

    private String getPriceRange(Integer price) {
        if (price < 10_000) {
            return "저가형";
        } else if (price < 50_000) {
            return "보급형";
        } else if (price < 100_000) {
            return "중급형";
        } else if (price < 500_000) {
            return "고급형";
        } else {
            return "프리미엄";
        }
    }
}
