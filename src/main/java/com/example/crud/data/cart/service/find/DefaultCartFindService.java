package com.example.crud.data.cart.service.find;

import com.example.crud.common.mapper.CartMapper;
import com.example.crud.common.security.SecurityUtil;
import com.example.crud.data.cart.converter.CartConverter;
import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.dto.response.CartResponse;
import com.example.crud.data.cart.service.internal.CartInternalService;
import com.example.crud.entity.Cart;
import com.example.crud.entity.CartItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultCartFindService implements CartFindService {

    private final CartMapper cartMapper;
    private final CartConverter cartConverter;
    private final SecurityUtil securityUtil;
    private final CartInternalService cartInternalService;

    @Override
    public CartDto getCartByAuthenticateMember() {
        Long memberId = securityUtil.getCurrentMemberId();
        CartDto cartDto = cartMapper.findCartByMemberId(memberId);

        if (cartDto == null) {
            return CartDto.createEmpty();
        }

        if (cartDto.getTotalPrice() == null) {
            int totalPrice = cartDto.getCartItems().stream()
                    .mapToInt(item -> item.getPrice() * item.getQuantity())
                    .sum();
            cartDto.setTotalPrice(totalPrice);
        }

        return cartDto;
    }

    @Override
    public CartResponse getSelectedItems(List<Long> cartItemIds) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = cartInternalService.getCart(memberId);
        return cartConverter.toResponseWithSelectedItems(cart, cartItemIds);
    }

    @Override
    public CartItem getCartItem(Long cartItemId) {
        Long memberId = securityUtil.getCurrentMemberId();
        Cart cart = cartInternalService.getCart(memberId);
        return cartInternalService.getCartItemFromCart(cart, cartItemId);
    }
}
