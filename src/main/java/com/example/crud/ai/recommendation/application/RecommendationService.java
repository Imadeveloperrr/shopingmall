package com.example.crud.ai.recommendation.application;

import com.example.crud.ai.recommendation.domain.dto.ProductMatch;
import com.example.crud.ai.embedding.infrastructure.EmbeddingClient;
import com.example.crud.ai.embedding.domain.repository.ProductVectorRepository;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Product;
import com.example.crud.repository.ProductRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecommendationService {

    private final EmbeddingClient embeddingClient;
    private final ProductVectorRepository vectorRepository;
    private final ProductRepository productRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public List<ProductResponseDto> recommend(String userMessage) {

        float[] queryVec = embeddingClient.embed(userMessage)
                .block(Duration.ofSeconds(3));

        List<ProductMatch> matches = vectorRepository.findTopN(queryVec, 20);

        // 필요 시 상품 상세를 한번에 로딩해 결합
        Map<Long, Product> productMap =
                productRepository.findAllById(
                        matches.stream().map(ProductMatch::id).toList()
                ).stream().collect(Collectors.toMap(Product::getNumber, p -> p));

        return matches.stream()
                .map(m -> mapToDto(productMap.get(m.id()), m.score()))
                .toList();
    }

    private ProductResponseDto mapToDto(Product p, double score) {
        ProductResponseDto dto = new ProductResponseDto();
        BeanUtils.copyProperties(p, dto);
        dto.setRelevance(score);
        return dto;
    }
}