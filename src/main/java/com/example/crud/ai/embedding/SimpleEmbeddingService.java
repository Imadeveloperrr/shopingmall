package com.example.crud.ai.embedding;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 실용적인 임베딩 서비스 - OpenAI API 또는 HuggingFace API 사용
 * 복잡한 자체 ML 서비스 대신 외부 API 활용
 */
@Service
@Slf4j
public class SimpleEmbeddingService {

    private final WebClient webClient;
    private final Map<String, float[]> embeddingCache = new ConcurrentHashMap<>();
    
    @Value("${openai.api.key:}")
    private String openaiApiKey;
    
    @Value("${huggingface.api.key:}")
    private String huggingfaceApiKey;
    
    public SimpleEmbeddingService(@Qualifier("embeddingWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * 텍스트를 임베딩 벡터로 변환 - 실제 OpenAI/HuggingFace API 사용
     */
    public float[] generateEmbedding(String text) {
        if (text == null || text.trim().isEmpty()) {
            return new float[384];
        }

        // 캠시 확인
        String cacheKey = "emb:" + text.hashCode();
        if (embeddingCache.containsKey(cacheKey)) {
            return embeddingCache.get(cacheKey);
        }

        try {
            float[] embedding = null;
            
            // 1순위: OpenAI API 사용 (가장 정확)
            if (openaiApiKey != null && !openaiApiKey.isEmpty()) {
                embedding = generateOpenAIEmbedding(text);
            }
            // 2순위: HuggingFace API 사용 (무료)
            else if (huggingfaceApiKey != null && !huggingfaceApiKey.isEmpty()) {
                embedding = generateHuggingFaceEmbedding(text);
            }
            // 3순위: 간단한 키워드 기반 벡터 (Fallback)
            else {
                embedding = generateKeywordBasedEmbedding(text);
            }

            // 캠싱 (최대 1000개)
            if (embeddingCache.size() < 1000) {
                embeddingCache.put(cacheKey, embedding);
            }

            log.debug("임베딩 생성: text length={}, method={}", 
                text.length(), 
                openaiApiKey != null ? "OpenAI" : 
                huggingfaceApiKey != null ? "HuggingFace" : "Keyword");
                
            return embedding;
            
        } catch (Exception e) {
            log.warn("임베딩 생성 실패, fallback 사용: {}", e.getMessage());
            return generateKeywordBasedEmbedding(text);
        }
    }

    /**
     * 벡터 유사도 계산 (코사인 유사도)
     */
    public double calculateSimilarity(float[] vector1, float[] vector2) {
        if (vector1.length != vector2.length) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vector1.length; i++) {
            dotProduct += vector1[i] * vector2[i];
            norm1 += vector1[i] * vector1[i];
            norm2 += vector2[i] * vector2[i];
        }

        if (norm1 == 0.0 || norm2 == 0.0) {
            return 0.0;
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    /**
     * OpenAI API로 임베딩 생성
     */
    private float[] generateOpenAIEmbedding(String text) {
        try {
            Map<String, Object> request = Map.of(
                "input", text,
                "model", "text-embedding-ada-002"
            );

            Map<String, Object> response = webClient.post()
                .uri("https://api.openai.com/v1/embeddings")
                .header("Authorization", "Bearer " + openaiApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(5))
                .block();

            List<Map<String, Object>> data = (List<Map<String, Object>>) response.get("data");
            List<Double> embedding = (List<Double>) data.get(0).get("embedding");
            
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i).floatValue();
            }
            return result;
                        
        } catch (Exception e) {
            throw new RuntimeException("OpenAI API 호출 실패: " + e.getMessage());
        }
    }

    /**
     * HuggingFace API로 임베딩 생성
     */
    private float[] generateHuggingFaceEmbedding(String text) {
        try {
            Map<String, Object> request = Map.of("inputs", text);

            List<List<Float>> response = webClient.post()
                .uri("https://api-inference.huggingface.co/models/sentence-transformers/all-MiniLM-L6-v2")
                .header("Authorization", "Bearer " + huggingfaceApiKey)
                .header("Content-Type", "application/json")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(List.class)
                .timeout(Duration.ofSeconds(10))
                .block();

            List<Float> embedding = response.get(0);
            float[] result = new float[embedding.size()];
            for (int i = 0; i < embedding.size(); i++) {
                result[i] = embedding.get(i);
            }
            return result;
                        
        } catch (Exception e) {
            throw new RuntimeException("HuggingFace API 호출 실패: " + e.getMessage());
        }
    }

    /**
     * 키워드 기반 임베딩 (Fallback)
     */
    private float[] generateKeywordBasedEmbedding(String text) {
        float[] embedding = new float[384];
        String normalizedText = text.toLowerCase();
        
        // 상품 관련 키워드 매핑
        Map<String, Integer> keywordMap = Map.ofEntries(
                Map.entry("옵트", 0), Map.entry("상의", 10), Map.entry("하의", 20), Map.entry("원피스", 30),
                Map.entry("신발", 40), Map.entry("가방", 50), Map.entry("모자", 60), Map.entry("액세서리", 70),
                Map.entry("화장품", 80), Map.entry("향수", 90), Map.entry("전자제품", 100), Map.entry("가전", 110)
        );
        
        // 키워드 매칭
        for (Map.Entry<String, Integer> entry : keywordMap.entrySet()) {
            if (normalizedText.contains(entry.getKey())) {
                int startIdx = entry.getValue();
                for (int i = 0; i < 32 && startIdx + i < 384; i++) {
                    embedding[startIdx + i] = 0.8f + (float) Math.random() * 0.2f;
                }
            }
        }
        
        // 텍스트 기본 정보 반영
        int textHash = text.hashCode();
        for (int i = 200; i < 384; i++) {
            embedding[i] = (float) Math.sin(textHash + i) * 0.3f;
        }
        
        return embedding;
    }
    
    /**
     * 배치 임베딩 생성
     */
    public float[][] generateBatchEmbeddings(String[] texts) {
        float[][] embeddings = new float[texts.length][];
        
        for (int i = 0; i < texts.length; i++) {
            embeddings[i] = generateEmbedding(texts[i]);
        }

        log.debug("배치 임베딩 생성: count={}", texts.length);
        return embeddings;
    }
}