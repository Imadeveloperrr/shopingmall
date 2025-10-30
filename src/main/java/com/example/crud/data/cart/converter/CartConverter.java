package com.example.crud.data.cart.converter;

import com.example.crud.data.cart.dto.checkout.CartCheckoutItem;
import com.example.crud.data.cart.dto.query.CartItemQueryDto;
import com.example.crud.data.cart.dto.query.CartQueryDto;
import com.example.crud.data.cart.dto.response.CartItemResponse;
import com.example.crud.data.cart.dto.response.CartResponse;
import java.util.List;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Cart Query DTO → 응답/도메인 DTO 변환기.
 */
@Mapper(componentModel = "spring")
public interface CartConverter {

    /**
     * MyBatis Query DTO → 응답 DTO 변환.
     */
    default CartResponse toResponse(CartQueryDto queryDto) {
        if (queryDto == null) {
            return CartResponse.empty();
        }

        List<CartItemQueryDto> items = queryDto.getCartItems();
        if (items == null || items.isEmpty()) {
            return CartResponse.empty();
        }

        List<CartItemQueryDto> validItems = items.stream()
            .filter(item -> item.getId() != null)
            .collect(Collectors.toList());

        if (validItems.isEmpty()) {
            return CartResponse.empty();
        }

        List<CartItemResponse> itemResponses = validItems.stream()
            .map(this::toItemResponse)
            .collect(Collectors.toList());

        return new CartResponse(
            queryDto.getId(),
            itemResponses,
            calculateTotalPrice(validItems)
        );
    }

    /**
     * Query Item → 응답 아이템 DTO.
     */
    @Mapping(source = "productSize", target = "productSize")
    @Mapping(source = "productColor", target = "productColor")
    CartItemResponse toItemResponse(CartItemQueryDto itemQueryDto);

    /**
     * Query Item → 주문용 DTO.
     */
    @Mapping(source = "id", target = "cartItemId")
    @Mapping(source = "productSize", target = "size")
    @Mapping(source = "productColor", target = "color")
    CartCheckoutItem toCheckoutItem(CartItemQueryDto itemQueryDto);

    /**
     * 필터링된 아이템 기준 총 금액 계산.
     */
    default Integer calculateTotalPrice(List<CartItemQueryDto> validItems) {
        if (validItems == null || validItems.isEmpty()) {
            return 0;
        }

        return validItems.stream()
            .mapToInt(item -> {
                Integer price = item.getPrice();
                Integer quantity = item.getQuantity();
                return (price != null && quantity != null) ? price * quantity : 0;
            })
            .sum();
    }
}
