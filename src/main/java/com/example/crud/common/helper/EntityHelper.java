package com.example.crud.common.helper;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.entity.*;
import com.example.crud.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.function.Supplier;

/**
 * 엔티티 조회 공통 로직
 * - 모든 Service/Util에서 반복되는 Optional.orElseThrows() 패턴을 제거
 * - 일갈된 예외 처리로 중복 코드 최소화
 */
@Component
@RequiredArgsConstructor
public class EntityHelper {

    private final ProductRepository productRepository;
    private final ProductOptionRepository productOptionRepository;
    private final MemberRepository memberRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final OrderRepository orderRepository;

    // ============== Product 관련 ==============

    /**
     * 상품 조회
     */
    public Product getProduct(Long productId) {
        return findOrThrow(
                () -> productRepository.findById(productId),
                ErrorCode.PRODUCT_NOT_FOUND, productId
        );
    }

    /**
     * 상품 옵션 조회
     */
    public ProductOption getProductOption(Long productId, String color, String size) {
        return findOrThrow(
                () -> productOptionRepository.findByProduct_NumberAndColorAndSize(
                        productId,
                        color,
                        size
                ),
                ErrorCode.PRODUCT_OPTION_NOT_FOUND,
                productId, color, size
        );
    }

    // ============== Member 관련 ==============

    /**
     * 회원 조회 (ID)
     */
    public Member getMember(Long memberId) {
        return findOrThrow(
                () -> memberRepository.findById(memberId),
                ErrorCode.MEMBER_NOT_FOUND
        );
    }

    /**
     * 회원 조회 (이메일)
     */
    public Member getMemberByEmail(String email) {
        return findOrThrow(
                () -> memberRepository.findByEmail(email),
                ErrorCode.MEMBER_NOT_FOUND
        );
    }

    // ============== Order 관련 ==============

    /**
     * 주몬 조회
     */
    public Orders getOrder(Long orderId) {
        return findOrThrow(
                () -> orderRepository.findById(orderId),
                ErrorCode.ORDER_NOT_FOUND, orderId
        );
    }

    // ============== RefreshTorken 관련 ==============

    /**
     * RefreshToken 조회
     */
    public RefreshToken getRefreshToken(String email) {
        return findOrThrow(
                () -> refreshTokenRepository.findById(email),
                ErrorCode.INVALID_REFRESH_TOKEN
        );
    }

    // ============== 공통 로직 ==============

    /**
     * Optional 조회 공통 메서드
     * - 모든 findById().orElseThrow() 패턴을 이 한곳으로 통합
     */
    private <T> T findOrThrow(Supplier<Optional<T>> finder, ErrorCode errorCode, Object... args) {
        return finder.get()
                .orElseThrow(() -> new BaseException(errorCode, args));
    }

}
