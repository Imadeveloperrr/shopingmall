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

        // 2. 카테고리 + 연관 키워드 (한국어 포함)
        if (product.getCategory() != null) {
            text.append(product.getCategory().name()).append(" ");
            text.append(product.getCategory().getGroupName()).append(" ");
            if (product.getSubCategory() != null && !product.getSubCategory().isBlank()) {
                text.append(product.getSubCategory()).append(" ");
            }
            text.append(categoryKeywords(product.getCategory()));
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

    /**
     * 카테고리별로 의미 있는 한국어/도메인 키워드를 추가해 임베딩 분리도를 높임
     */
    private String categoryKeywords(com.example.crud.enums.Category category) {
        return switch (category) {
            case OUTER -> "옷 의류 아우터 겨울용 코트 패딩 자켓 따뜻한 보온 보온성";
            case TOP -> "옷 의류 상의 티셔츠 니트 스웨터 후드 맨투맨 셔츠";
            case BOTTOM -> "옷 의류 하의 청바지 슬랙스 면바지 바지";
            case DRESS -> "옷 의류 원피스 드레스 스커트 데일리룩";
            case SHOES -> "신발 운동화 부츠 구두 러닝화 워킹화";
            case BAG -> "패션잡화 가방 백 백팩 크로스백 숄더백 토트백 액세서리";
            case ACCESSORY -> "패션잡화 악세서리 액세서리 목걸이 반지 귀걸이 팔찌";
        };
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
