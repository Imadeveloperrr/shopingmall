package com.example.crud.data.cart.service.update;

import com.example.crud.common.security.SecurityUtil;
import com.example.crud.data.cart.service.internal.CartInternalService;
import com.example.crud.data.cart.validator.CartValidator;
import com.example.crud.data.product.service.ProductFindService;
import com.example.crud.entity.Cart;
import com.example.crud.entity.CartItem;
import com.example.crud.entity.ProductOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 아이템 수정 서비스
 * - 인터페이스 없음 (단일 구현만 필요)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UpdateCartItemService {

    private final SecurityUtil securityUtil;
    private final CartInternalService cartInternalService;
    private final ProductFindService productFindService;
    private final CartValidator cartValidator;

    @Transactional
    public void updateQuantity(Long cartItemId, int quantity) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = cartInternalService.getCart(memberId);

        CartItem cartItem = cartInternalService.getCartItemFromCart(cart, cartItemId);

        cartValidator.validateNewQuantity(cartItem.getQuantity(), quantity);

        int newQuantity = cartItem.getQuantity() + quantity;
        cartValidator.validateStock(cartItem.getProductOption(), newQuantity);

        cartItem.updateQuantity(quantity);
        // JPA 더티체킹으로 자동 저장
    }

    @Transactional
    public void updateOption(Long cartItemId, String newColor, String newSize) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = cartInternalService.getCart(memberId);

        CartItem cartItem = cartInternalService.getCartItemFromCart(cart, cartItemId);

        ProductOption newOption = productFindService.getProductOption(
                cartItem.getProduct().getNumber(),
                newColor,
                newSize
        );

        cartValidator.validateStock(newOption, cartItem.getQuantity());

        cartItem.changeOption(newOption);
        // JPA 더티체킹으로 자동 저장
    }
}
