package com.example.crud.data.cart.service.add;

import com.example.crud.common.security.SecurityUtil;
import com.example.crud.data.cart.service.create.CreateCartService;
import com.example.crud.data.cart.validator.CartValidator;
import com.example.crud.data.product.service.ProductFindService;
import com.example.crud.entity.Cart;
import com.example.crud.entity.CartItem;
import com.example.crud.entity.Product;
import com.example.crud.entity.ProductOption;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 장바구니 아이템 추가 서비스
 * - 인터페이스 없음 (단일 구현만 필요)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AddCartItemService {

    private final SecurityUtil securityUtil;
    private final ProductFindService productFindService;
    private final CartValidator cartValidator;
    private final CreateCartService createCartService;

    @Transactional
    public void addCartItem(Long productId, String color, String size, int quantity) {
        Long memberId = securityUtil.getCurrentMemberId();
        Product product = productFindService.getProduct(productId);
        ProductOption productOption = productFindService.getProductOption(productId, color, size);

        cartValidator.validateStock(productOption, quantity);

        Cart cart = createCartService.getOrCreateCart(memberId);

        cart.getCartItems().stream()
            .filter(item -> item.isSameProductAndOptionById(product.getNumber(), productOption.getId()))
            .findFirst()
            .ifPresentOrElse(
                existingItem -> {
                    int newQuantity = existingItem.getQuantity() + quantity;
                    cartValidator.validateStock(productOption, newQuantity);
                    existingItem.increaseQuantity(quantity);
                },
                () -> {
                    CartItem newCartItem = CartItem.create(product, productOption, quantity);
                    cart.addItem(newCartItem);
                }
            );

        // JPA 더티체킹으로 자동 저장
    }
}
