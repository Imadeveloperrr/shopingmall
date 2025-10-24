package com.example.crud.data.cart.validator;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.entity.Cart;
import com.example.crud.entity.CartItem;
import com.example.crud.entity.Member;
import com.example.crud.entity.ProductOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 장바구니 관련 검증 로직
 * - 수량 유효성 검증
 * - 재고 충분 여부 검증
 * - 장바구니 소유자 검증
 */
@Component
@RequiredArgsConstructor
public class CartValidator {

    /**
     * 수량 유효성 검증
     *
     * @param quantity 수량
     * @throws BaseException 수량이 0 이하인 경우
     */
    public void validateQuantity(int quantity) {
        if (quantity <= 0) {
            throw new BaseException(ErrorCode.INVALID_QUANTITY, quantity);
        }
    }

    /**
     * 재고 충분 여부 검증
     *
     * @param option 상품 옵션
     * @param quantity 요청 수량
     * @throws BaseException 재고가 부족한 경우
     */
    public void validateStock(ProductOption option, int quantity) {
        if (option.getStock() < quantity) {
            throw new BaseException(
                ErrorCode.CART_INSUFFICIENT_STOCK,
                option.getStock(),
                quantity
            );
        }
    }

    /**
     * 장바구니 소유자 검증
     *
     * @param cart 장바구니
     * @param memberId 회원 ID
     * @throws BaseException 장바구니 소유자가 아닌 경우
     */
    public void validateCartOwner(Cart cart, Long memberId) {
        if (!cart.getMember().getNumber().equals(memberId)) {
            throw new BaseException(ErrorCode.FORBIDDEN);
        }
    }

    /**
     * 장바구니 아이템이 장바구니에 속하는지 검증
     *
     * @param cart 장바구니
     * @param cartItemId 장바구니 아이템 ID
     * @throws BaseException 장바구니에 해당 아이템이 없는 경우
     */
    public void validateCartItemBelongsToCart(Cart cart, Long cartItemId) {
        boolean exists = cart.getCartItems().stream()
                .anyMatch(item -> item.getId().equals(cartItemId));

        if (!exists) {
            throw new BaseException(ErrorCode.CART_ITEM_NOT_FOUND, cartItemId);
        }
    }

    /**
     * 새로운 수량 검증 (수량 업데이트 시)
     *
     * @param currentQuantity 현재 수량
     * @param quantityChange 수량 변경값
     * @throws BaseException 새로운 수량이 0 이하인 경우
     */
    public void validateNewQuantity(int currentQuantity, int quantityChange) {
        int newQuantity = currentQuantity + quantityChange;
        if (newQuantity <= 0) {
            throw new BaseException(ErrorCode.CART_INSUFFICIENT_QUANTITY, newQuantity);
        }
    }
}
