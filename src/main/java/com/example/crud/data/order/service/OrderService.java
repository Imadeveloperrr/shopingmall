package com.example.crud.data.order.service;

import com.example.crud.data.cart.dto.checkout.CartCheckoutItem;
import com.example.crud.data.order.dto.OrderDto;
import com.example.crud.data.order.dto.OrderItemDto;
import com.example.crud.data.order.dto.OrderPreparationDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.Orders;
import com.example.crud.enums.OrderStatus;
import java.util.List;

public interface OrderService {

    List<OrderItemDto> prepareOrderItems(List<CartCheckoutItem> checkoutItems);

    OrderPreparationDto prepareDirectOrder(ProductResponseDto product, String color, String size, int quantity);

    OrderPreparationDto prepareOrder(List<OrderItemDto> orderItems);

    Orders createOrder(OrderDto orderDto);

    Orders getOrder(Long orderId);

    void cancelOrder(Long orderId);

    void updateOrderStatus(Long orderId, OrderStatus newStatus);

    List<Orders> getMemberOrders(Long memberId);

    boolean checkStock(Long productId, String color, String size, int quantity);

    int calculateDeliveryFee(int totalAmount);
}
