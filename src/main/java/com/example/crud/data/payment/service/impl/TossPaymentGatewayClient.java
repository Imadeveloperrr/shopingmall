package com.example.crud.data.payment.service.impl;

import com.example.crud.data.payment.dto.PaymentDto;
import com.example.crud.data.payment.dto.PaymentGatewayResponse;
import com.example.crud.data.payment.service.PaymentGatewayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;

/**
 * 실제 Toss Payments API 연동 클라이언트
 *
 * 설정 필요:
 * - application.properties에 toss.payments.secret-key 추가
 * - 프론트엔드에서 실제 결제 완료 후 백엔드 확인 플로우 연동
 */
@Service
@Slf4j
public class TossPaymentGatewayClient implements PaymentGatewayClient {

    private final WebClient webClient;
    private final String secretKey;

    // Toss Payments API Base URL
    private static final String TOSS_API_BASE_URL = "https://api.tosspayments.com/v1";

    public TossPaymentGatewayClient(
            WebClient.Builder webClientBuilder,
            @Value("${toss.payments.secret-key:}") String secretKey) {
        this.webClient = webClientBuilder
                .baseUrl(TOSS_API_BASE_URL)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.secretKey = secretKey;
    }

    @Override
    public PaymentGatewayResponse processPayment(PaymentDto paymentDto) {
        try {
            // Toss Payments는 프론트엔드에서 결제 후 백엔드에서 확인하는 방식
            // 여기서는 결제 승인 확인 API 호출
            return confirmPayment(paymentDto);

        } catch (Exception e) {
            log.error("Toss Payments 결제 처리 실패: {}", e.getMessage(), e);

            PaymentGatewayResponse response = new PaymentGatewayResponse();
            response.setSuccess(false);
            response.setTransactionId(null);
            response.setErrorMessage("결제 처리 중 오류가 발생했습니다: " + e.getMessage());
            return response;
        }
    }

    /**
     * Toss Payments 결제 승인 확인
     * 실제로는 프론트엔드에서 paymentKey를 받아서 처리해야 함
     */
    private PaymentGatewayResponse confirmPayment(PaymentDto paymentDto) {
        try {
            // Basic Auth 헤더 생성 (Secret Key + 콜론)
            String auth = secretKey + ":";
            String encodedAuth = Base64.getEncoder()
                    .encodeToString(auth.getBytes(StandardCharsets.UTF_8));

            // 결제 승인 요청 데이터
            Map<String, Object> confirmRequest = Map.of(
                    "paymentKey", paymentDto.getTransactionId(), // 프론트엔드에서 받은 paymentKey
                    "orderId", paymentDto.getOrderId().toString(),
                    "amount", paymentDto.getAmount()
            );

            Map<String, Object> response = webClient.post()
                    .uri("/payments/confirm")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                    .bodyValue(confirmRequest)
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();

            // 성공 응답 처리
            PaymentGatewayResponse gatewayResponse = new PaymentGatewayResponse();

            String status = (String) response.get("status");
            if ("DONE".equals(status)) {
                gatewayResponse.setSuccess(true);
                gatewayResponse.setTransactionId((String) response.get("paymentKey"));
                gatewayResponse.setErrorMessage(null);

                log.info("Toss Payments 결제 승인 성공: orderId={}, paymentKey={}",
                        paymentDto.getOrderId(), response.get("paymentKey"));
            } else {
                gatewayResponse.setSuccess(false);
                gatewayResponse.setTransactionId(null);
                gatewayResponse.setErrorMessage("결제 상태가 완료되지 않음: " + status);
            }

            return gatewayResponse;

        } catch (WebClientResponseException e) {
            log.error("Toss Payments API 호출 실패: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());

            PaymentGatewayResponse response = new PaymentGatewayResponse();
            response.setSuccess(false);
            response.setTransactionId(null);
            response.setErrorMessage("결제 승인 실패: " + e.getResponseBodyAsString());
            return response;
        }
    }
}