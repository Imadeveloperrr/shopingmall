package com.example.crud.data.cart.converter;

import com.example.crud.data.cart.dto.response.CartItemResponse;
import com.example.crud.data.cart.dto.response.CartResponse;
import com.example.crud.entity.Cart;
import com.example.crud.entity.CartItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Cart Entity ↔ DTO 변환
 *
 * 주의: MyBatis CartMapper와 이름 충돌 방지 위해 'Converter' 네이밍 사용
 */
@Mapper(componentModel = "spring")
public interface CartConverter {

    /**
     * CartItem Entity → CartItemResponse
     */
    @Mapping(target = "productId", source = "product.number")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "productSize", source = "productOption.size")
    @Mapping(target = "productColor", source = "productOption.color")
    @Mapping(target = "price", source = "product.price")
    @Mapping(target = "imageUrl", source = "product.imageUrl")
    CartItemResponse toItemResponse(CartItem cartItem);

    /**
     * List<CartItem> → List<CartItemResponse>
     */
    List<CartItemResponse> toItemResponseList(List<CartItem> cartItems);

    /**
     * Cart Entity → CartResponse
     */
    default CartResponse toResponse(Cart cart) {
        if (cart == null) {
            return CartResponse.empty();
        }

        List<CartItemResponse> items = toItemResponseList(cart.getCartItems());
        int totalPrice = CartResponse.calculateTotalPrice(items);

        return new CartResponse(cart.getId(), items, totalPrice);
    }

    /**
     * 선택된 아이템만 CartResponse 생성
     */
    default CartResponse toResponseWithSelectedItems(Cart cart, List<Long> cartItemIds) {
        if (cart == null || cartItemIds == null || cartItemIds.isEmpty()) {
            return CartResponse.empty();
        }

        // 중복 허용하며 Set으로 변환
        Set<Long> selectedIds = new HashSet<>(cartItemIds);

        List<CartItem> selectedItems = cart.getCartItems().stream()
            .filter(item -> selectedIds.contains(item.getId()))
            .collect(Collectors.toList());

        // toItemResponseList 재사용
        List<CartItemResponse> items = toItemResponseList(selectedItems);
        int totalPrice = CartResponse.calculateTotalPrice(items);

        return new CartResponse(cart.getId(), items, totalPrice);
    }
}
