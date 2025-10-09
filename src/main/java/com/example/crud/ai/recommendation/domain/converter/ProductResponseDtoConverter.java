package com.example.crud.ai.recommendation.domain.converter;

import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * ProductMatch를 ProductResponseDto로 변환하는 컨버터
 *
 * RecommendationTestController와 ConversationalRecommendationService에서 공통으로 사용
 */
@Component
@RequiredArgsConstructor
public class ProductResponseDtoConverter {

    private final ProductRepository productRepository;

    // ThreadLocal NumberFormat for price formatting (thread-safe)
    private static final ThreadLocal<NumberFormat> PRICE_FORMATTER =
            ThreadLocal.withInitial(() -> NumberFormat.getNumberInstance(Locale.KOREA));

    /**
     * ProductMatch 리스트를 ProductResponseDto 리스트로 변환
     *
     * @param matches ProductMatch 리스트
     * @return ProductResponseDto 리스트
     */
    public List<ProductResponseDto> convertToProductResponseDtos(List<ProductMatch> matches) {
        List<Long> productIds = matches.stream()
                .map(ProductMatch::id)
                .toList();

        // 한 번의 쿼리로 모든 Product 조회 (N+1 방지)
        Map<Long, Product> productMap = productRepository.findAllById(productIds)
                .stream()
                .collect(Collectors.toMap(Product::getNumber, p -> p));

        return matches.stream()
                .map(match -> convertToProductResponseDto(match, productMap.get(match.id())))
                .collect(Collectors.toList());
    }

    /**
     * 단일 ProductMatch를 ProductResponseDto로 변환
     *
     * @param match ProductMatch
     * @param product Product 엔티티 (null 가능)
     * @return ProductResponseDto
     */
    private ProductResponseDto convertToProductResponseDto(ProductMatch match, Product product) {
        ProductResponseDto dto = new ProductResponseDto();

        if (product == null) {
            // Product를 찾을 수 없는 경우 기본 DTO 반환
            dto.setNumber(match.id());
            dto.setName(match.name());
            return dto;
        }

        // Direct mapping (BeanUtils.copyProperties 대신 직접 매핑으로 성능 최적화)
        dto.setNumber(product.getNumber());
        dto.setName(product.getName());

        // 가격 포맷팅
        if (product.getPrice() != null) {
            dto.setPrice(PRICE_FORMATTER.get().format(product.getPrice()) + "원");
        }

        // 카테고리 설정
        if (product.getCategory() != null) {
            dto.setCategory(product.getCategory().name());
        }

        // 설명 줄바꿈 처리 (HTML 표시용)
        if (product.getDescription() != null) {
            dto.setDescription(product.getDescription().replace("\n", "<br>"));
        }

        // 매칭 점수 (유사도) 추가
        dto.setRelevance(match.score());

        return dto;
    }
}
