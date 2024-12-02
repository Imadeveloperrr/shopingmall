package com.example.crud.data.order.service;

import com.example.crud.data.cart.dto.CartItemDto;
import com.example.crud.data.order.dto.OrderDto;
import com.example.crud.data.order.dto.OrderItemDto;
import com.example.crud.data.order.dto.OrderPreparationDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Orders;
import com.example.crud.enums.OrderStatus;

import java.util.List;

public interface OrderService {

    // 장바구니에서 주문 준비
    List<OrderItemDto> prepareOrderItems(List<CartItemDto> cartItems);

    // 직접 주문 준비
    OrderPreparationDto prepareDirectOrder(ProductResponseDto product, String color, String size, int quantity);

    // 주문 준비 (공통)
    OrderPreparationDto prepareOrder(List<OrderItemDto> orderItems);

    // 주문 생성
    Orders createOrder(OrderDto orderDto);

    // 주문 조회
    Orders getOrder(Long orderId);

    // 주문 취소
    void cancelOrder(Long orderId);

    // 주문 상태 변경
    void updateOrderStatus(Long orderId, OrderStatus newStatus);

    // 회원의 주문 목록 조회
    List<Orders> getMemberOrders(Long memberId);

    // 재고 확인
    boolean checkStock(Long productId, String color, String size, int quantity);

    // 배송비 계산
    int calculateDeliveryFee(int totalAmount);
}
