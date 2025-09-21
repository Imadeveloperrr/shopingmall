package com.example.crud.common.config;

import com.example.crud.data.payment.service.PaymentGatewayClient;
import com.example.crud.data.payment.service.impl.DummyPaymentGatewayClient;
import com.example.crud.data.payment.service.impl.TossPaymentGatewayClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 결제 시스템 설정
 *
 * 개발/테스트 환경: DummyPaymentGatewayClient (기본값)
 * 운영 환경: TossPaymentGatewayClient
 *
 * 설정 방법:
 * # application.properties (운영 환경)
 * payment.gateway.mode=real
 * toss.payments.secret-key=실제_시크릿_키
 *
 * # application.properties (개발 환경)
 * payment.gateway.mode=dummy
 */
@Configuration
@Slf4j
public class PaymentConfig {

    @Value("${payment.gateway.mode:dummy}")
    private String paymentMode;

    @Value("${toss.payments.secret-key:}")
    private String tossSecretKey;

    /**
     * 운영 환경: 실제 Toss Payments 연동
     */
    @Bean
    @Primary
    @ConditionalOnProperty(name = "payment.gateway.mode", havingValue = "real")
    public PaymentGatewayClient realPaymentGatewayClient(WebClient.Builder webClientBuilder) {
        if (tossSecretKey == null || tossSecretKey.trim().isEmpty()) {
            log.error("❌ 실제 결제 모드이지만 toss.payments.secret-key가 설정되지 않았습니다!");
            throw new IllegalStateException("Toss Payments Secret Key가 필요합니다.");
        }

        log.info("✅ 실제 결제 모드 활성화: Toss Payments 연동");
        return new TossPaymentGatewayClient(webClientBuilder, tossSecretKey);
    }

    /**
     * 개발/테스트 환경: 더미 결제 처리 (기본값)
     */
    @Bean
    @ConditionalOnProperty(name = "payment.gateway.mode", havingValue = "dummy", matchIfMissing = true)
    public PaymentGatewayClient dummyPaymentGatewayClient() {
        log.warn("⚠️  더미 결제 모드 활성화: 실제 결제가 처리되지 않습니다.");
        log.warn("⚠️  운영 환경에서는 payment.gateway.mode=real로 설정하세요.");
        return new DummyPaymentGatewayClient();
    }

    /**
     * WebClient 빈 설정 (필요한 경우에만)
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}