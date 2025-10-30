package com.example.crud.data.cart.service.find;

import com.example.crud.common.mapper.CartMapper;
import com.example.crud.common.security.SecurityUtil;
import com.example.crud.data.cart.converter.CartConverter;
import com.example.crud.data.cart.dto.checkout.CartCheckoutItem;
import com.example.crud.data.cart.dto.query.CartItemQueryDto;
import com.example.crud.data.cart.dto.query.CartQueryDto;
import com.example.crud.data.cart.dto.response.CartResponse;
import com.example.crud.data.cart.service.internal.CartInternalService;
import com.example.crud.entity.Cart;
import com.example.crud.entity.CartItem;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultCartFindService implements CartFindService {

    private final CartMapper cartMapper;
    private final CartConverter cartConverter;
    private final SecurityUtil securityUtil;
    private final CartInternalService cartInternalService;

    @Override
    public CartResponse getCart() {
        Long memberId = securityUtil.getCurrentMemberId();
        CartQueryDto queryDto = cartMapper.findCartByMemberId(memberId);

        if (queryDto == null || queryDto.getCartItems() == null || queryDto.getCartItems().isEmpty()) {
            return CartResponse.empty();
        }

        return cartConverter.toResponse(queryDto);
    }

    @Override
    public CartResponse getSelectedItems(List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return CartResponse.empty();
        }

        Long memberId = securityUtil.getCurrentMemberId();
        CartQueryDto queryDto = cartMapper.findCartByMemberId(memberId);

        if (queryDto == null || queryDto.getCartItems() == null) {
            return CartResponse.empty();
        }

        List<CartItemQueryDto> selectedItems = queryDto.getCartItems().stream()
            .filter(item -> item.getId() != null)
            .filter(item -> cartItemIds.contains(item.getId()))
            .collect(Collectors.toList());

        if (selectedItems.isEmpty()) {
            return CartResponse.empty();
        }

        CartQueryDto filtered = CartQueryDto.builder()
            .id(queryDto.getId())
            .cartItems(selectedItems)
            .build();

        return cartConverter.toResponse(filtered);
    }

    @Override
    public List<CartCheckoutItem> getCheckoutItems(List<Long> cartItemIds) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> distinctIds = cartItemIds.stream()
            .distinct()
            .collect(Collectors.toList());

        Long memberId = securityUtil.getCurrentMemberId();
        CartQueryDto queryDto = cartMapper.findCartByMemberId(memberId);

        if (queryDto == null || queryDto.getCartItems() == null) {
            return Collections.emptyList();
        }

        List<CartCheckoutItem> items = queryDto.getCartItems().stream()
            .filter(item -> item.getId() != null)
            .filter(item -> distinctIds.contains(item.getId()))
            .map(cartConverter::toCheckoutItem)
            .collect(Collectors.toList());

        if (items.size() != distinctIds.size()) {
            throw new IllegalArgumentException(
                String.format(
                    "요청한 장바구니 아이템 중 일부를 찾을 수 없거나 권한이 없습니다. (요청: %d개, 조회: %d개, 원본 요청: %d개)",
                    distinctIds.size(),
                    items.size(),
                    cartItemIds.size()
                )
            );
        }

        return items;
    }

    @Override
    public CartItem getCartItem(Long cartItemId) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = cartInternalService.getCart(memberId);
        return cartInternalService.getCartItemFromCart(cart, cartItemId);
    }
}
