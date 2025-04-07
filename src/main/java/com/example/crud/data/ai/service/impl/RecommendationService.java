package com.example.crud.data.ai.service.impl;

import com.example.crud.data.ai.dto.ProductResponseDto;
import com.example.crud.data.ai.dto.Preference;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final ProductRepository productRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * ChatGPT로 추출된 상세 사용자 의도 정보를 기반으로
     * 고정밀 상품 추천을 수행합니다.
     * 상품 등록 시 이미 ChatGPT를 통해 사용자의 복합적이고 세부적인 요구사항이 반영된
     * DB 상품 데이터를 활용하여 매우 높은 정확도의 추천 결과를 산출합니다.
     *
     * @param userIntentJson ChatGPT로 추출된 사용자 의도 JSON 문자열
     * @return 추천 상품 목록 (최대 상위 10개)
     */
    public List<ProductResponseDto> getHighPrecisionRecommendations(String userIntentJson) {
        // 사용자 의도 정보 파싱
        Preference userIntent;
        try {
            userIntent = objectMapper.readValue(userIntentJson, Preference.class);
        } catch (Exception e) {
            log.error("사용자 의도 정보 파싱 실패: {}", e.getMessage());
            throw new RuntimeException("사용자 의도 정보 파싱 실패", e);
        }

        // 고정밀 추천 로직:
        // DB에 저장된 상품 데이터를 대상으로, 사용자가 ChatGPT로 추출한 상세 의도와 일치하는 상품을 조회합니다.
        // 실제 업무에서는 동적 쿼리나 Specification API 등을 활용하여 상세 조건에 맞게 조회할 수 있습니다.
        List<Product> highPrecisionProducts = productRepository.findHighPrecisionRecommendations(userIntent);
        log.info("고정밀 추천 상품 수: {}", highPrecisionProducts.size());

        // 조회된 상품들을 DTO로 변환 (최대 상위 10개)
        return highPrecisionProducts.stream()
                .limit(10)
                .map(product -> {
                    ProductResponseDto dto = new ProductResponseDto();
                    BeanUtils.copyProperties(product, dto);
                    return dto;
                })
                .collect(Collectors.toList());
    }
}
