package com.example.crud.data.cart.dto.checkout;

/**
 * 장바구니에서 주문 도메인으로 전달되는 체크아웃용 DTO.
 */
public record CartCheckoutItem(
    Long cartItemId,
    Long productId,
    String productName,
    String color,
    String size,
    Integer price,
    Integer quantity,
    String imageUrl
) {

    public int totalPrice() {
        return price * quantity;
    }
}
