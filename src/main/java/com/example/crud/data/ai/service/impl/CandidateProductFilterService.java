package com.example.crud.data.ai.service.impl;

import com.example.crud.entity.Product;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 후보 상품을 사용자가 원하는 느낌(예, 선호 스타일)과 허깅페이스에서 추출한 키워드를 기반으로 필터링하는 서비스
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CandidateProductFilterService {

    private final ProductDescriptionAnalyzer descriptionAnalyzer;

    /**
     * 허깅페이스에서 추출한 키워드를 기반으로 후보 상품을 필터링합니다.
     * 사용자 선호 스타일과 매칭되는 키워드가 최소 minKeywordMatch 이상 존재하는 상품만 반환합니다.
     *
     * @param candidateProducts 필터링할 상품 리스트
     * @param userStyle 사용자가 선호하는 스타일 (예: "모던", "빈티지" 등)
     * @param minKeywordMatch 매칭되어야 하는 최소 키워드 수
     * @return 필터링된 상품 리스트
     */
    public List<Product> filterCandidatesByKeywords(List<Product> candidateProducts, String userStyle, int minKeywordMatch) {
        List<Product> filtered = new ArrayList<>();
        for (Product product : candidateProducts) {
            // 허깅페이스나 기타 NLP 모듈을 통해 상품 설명에서 키워드 추출
            List<String> keywords = descriptionAnalyzer.extractKeywords(product.getDescription());
            long matchCount = keywords.stream()
                    .filter(keyword -> keyword.toLowerCase().contains(userStyle.toLowerCase()))
                    .count();
            if (matchCount >= minKeywordMatch) {
                filtered.add(product);
            }
        }
        log.info("필터링된 후보 상품 수: {}", filtered.size());
        return filtered;
    }
}
