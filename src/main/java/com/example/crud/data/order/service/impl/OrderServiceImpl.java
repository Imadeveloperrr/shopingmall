package com.example.crud.data.order.service.impl;

import com.example.crud.data.cart.dto.CartItemDto;
import com.example.crud.data.cart.service.CartService;
import com.example.crud.data.order.dto.DirectOrderInfo;
import com.example.crud.data.order.dto.OrderDto;
import com.example.crud.data.order.dto.OrderItemDto;
import com.example.crud.data.order.dto.OrderPreparationDto;
import com.example.crud.data.order.service.OrderService;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.entity.*;
import com.example.crud.enums.OrderStatus;
import com.example.crud.enums.OrderType;
import com.example.crud.repository.MemberRepository;
import com.example.crud.repository.OrderRepository;
import com.example.crud.repository.ProductRepository;
import com.example.crud.repository.ProductSizeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final MemberRepository memberRepository;
    private final ProductRepository productRepository;
    private final ProductSizeRepository productSizeRepository;
    private final CartService cartService;

    @Override
    public List<OrderItemDto> prepareOrderItems(List<CartItemDto> cartItems) {
        return cartItems.stream()
                .map(this::convertToOrderItemDto)
                .collect(Collectors.toList());
    }

    @Override
    public OrderPreparationDto prepareDirectOrder(ProductResponseDto product) {
        OrderItemDto orderItemDto = OrderItemDto.builder()
                .productId(product.getNumber())
                .productName(product.getName())
                .price(parsePrice(product.getPrice()))
                .quantity(1)
                .imageUrl(product.getImageUrl())
                .size(product.getProductSizes().get(0).getSize())
                .stockAvailable(true)
                .build();

        List<OrderItemDto> orderItems = List.of(orderItemDto);
        return prepareOrder(orderItems);
    }

    @Override
    public OrderPreparationDto prepareOrder(List<OrderItemDto> orderItems) {
        int totalAmount = calculateTotalAmount(orderItems);
        int deliveryFee = calculateDeliveryFee(totalAmount);

        return OrderPreparationDto.builder()
                .orderItems(orderItems)
                .totalAmount(totalAmount)
                .deliveryFee(deliveryFee)
                .finalAmount(totalAmount + deliveryFee)
                .isDirectOrder(false)
                .build();
    }

    @Override
    @Transactional
    public Orders createOrder(OrderDto orderDto) {
        Member member = getCurrentMember();

        // 주문 생성
        Orders order = Orders.builder()
                .member(member)
                .orderDate(LocalDateTime.now())
                .status(OrderStatus.ORDER_PLACED)
                .totalAmount(orderDto.getTotalAmount())
                .deliveryFee(orderDto.getDeliveryFee())
                .deliveryMethod(orderDto.getDeliveryMethod())
                .deliveryMemo(orderDto.getDeliveryMemo())
                .receiverName(orderDto.getReceiverName())
                .receiverPhone(orderDto.getReceiverPhone())
                .receiverMobile(orderDto.getReceiverMobile())
                .receiverAddress(orderDto.getReceiverAddress())
                .paymentMethod(orderDto.getPaymentMethod().name())
                .paymentStatus("PENDING")
                .build();

        // 주문 타입에 따른 처리
        if (orderDto.getOrderType() == OrderType.CART) {
            createOrderItemsFromCart(order, orderDto.getCartItemIds());
        } else {
            createDirectOrderItem(order, orderDto.getDirectOrderInfo());
        }

        // createOrderItems는 createOrder의 트랜잭션 내에서 실행됨
        createOrderItems(order, orderDto.getOrderItems());

        return orderRepository.save(order);
    }

    // 장바구니에서 주문 아이템 생성
    private void createOrderItemsFromCart(Orders order, List<Long> cartItemIds) {
        cartItemIds.forEach(cartItemId -> {
            CartItem cartItem = cartService.getCartItem(cartItemId);
            validateStock(cartItem.getProduct().getNumber(),
                    cartItem.getProductSize().getSize(),
                    cartItem.getQuantity());

            OrderItem orderItem = OrderItem.builder()
                    .orders(order)
                    .product(cartItem.getProduct())
                    .productSizeEntity(cartItem.getProductSize())
                    .quantity(cartItem.getQuantity())
                    .price(cartItem.getProduct().getPrice())
                    .productName(cartItem.getProduct().getName())
                    .productSize(cartItem.getProductSize().getSize())
                    .build();

            order.addOrderItem(orderItem);
            updateStock(cartItem.getProductSize(), cartItem.getQuantity());
        });
    }

    // 직접 주문 아이템 생성
    private void createDirectOrderItem(Orders order, DirectOrderInfo directOrder) {
        Product product = productRepository.findById(directOrder.getProductId())
                .orElseThrow(() -> new NoSuchElementException("상품을 찾을 수 없습니다."));

        validateStock(directOrder.getProductId(),
                directOrder.getSize(),
                directOrder.getQuantity());

        ProductSize productSize = productSizeRepository.findByProduct_NumberAndSize(
                        directOrder.getProductId(), directOrder.getSize())
                .orElseThrow(() -> new NoSuchElementException("해당 사이즈를 찾을 수 없습니다."));

        OrderItem orderItem = OrderItem.builder()
                .orders(order)
                .product(product)
                .productSizeEntity(productSize)
                .quantity(directOrder.getQuantity())
                .price(product.getPrice())
                .productName(product.getName())
                .productSize(directOrder.getSize())
                .build();

        order.addOrderItem(orderItem);
        updateStock(productSize, directOrder.getQuantity());
    }

    // 재고 검증
    private void validateStock(Long productId, String size, int quantity) {
        if (!checkStock(productId, size, quantity)) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
    }

    // 재고 업데이트
    private void updateStock(ProductSize productSize, int quantity) {
        productSize.setStock(productSize.getStock() - quantity);
        productSizeRepository.save(productSize);
    }

    @Override
    public Orders getOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("주문을 찾을 수 없습니다."));
    }

    @Override
    @Transactional
    public void cancelOrder(Long orderId) {
        Orders order = getOrder(orderId);
        order.cancel();
        orderRepository.save(order);
    }

    @Override
    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus newStatus) {
        Orders order = getOrder(orderId);
        order.setStatus(newStatus);
        orderRepository.save(order);
    }

    @Override
    public List<Orders> getMemberOrders(Long memberId) {
        return orderRepository.findByMemberNumberOrderByOrderDateDesc(memberId);
    }

    @Override
    public boolean checkStock(Long productId, String size, int quantity) {
        ProductSize productSize = productSizeRepository.findByProduct_NumberAndSize(productId, size)
                .orElseThrow(() -> new NoSuchElementException("해당 사이즈의 상품을 찾을 수 없습니다."));
        return productSize.getStock() >= quantity;
    }

    @Override
    public int calculateDeliveryFee(int totalAmount) {
        final int FREE_DELIVERY_THRESHOLD = 80000;
        final int STANDARD_DELIVERY_FEE = 3000;

        return totalAmount >= FREE_DELIVERY_THRESHOLD ? 0 : STANDARD_DELIVERY_FEE;
    }

    private Member getCurrentMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String email = authentication.getName();
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다."));
    }

    private OrderItemDto convertToOrderItemDto(CartItemDto cartItem) {
        return OrderItemDto.builder()
                .productName(cartItem.getProductName())
                .size(cartItem.getProductSize())
                .price(cartItem.getPrice())
                .quantity(cartItem.getQuantity())
                .imageUrl(cartItem.getImageUrl())
                .stockAvailable(true)
                .build();
    }

    private int calculateTotalAmount(List<OrderItemDto> orderItems) {
        return orderItems.stream()
                .mapToInt(item -> item.getPrice() * item.getQuantity())
                .sum();
    }

    private int parsePrice(String priceStr) {
        return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
    }

    private void createOrderItems(Orders order, List<OrderItemDto> orderItemDtos) {
        for (OrderItemDto itemDto : orderItemDtos) {
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new NoSuchElementException("상품을 찾을 수 없습니다."));

            ProductSize productSize = productSizeRepository.findByProduct_NumberAndSize(
                            itemDto.getProductId(), itemDto.getSize())
                    .orElseThrow(() -> new NoSuchElementException("해당 사이즈를 찾을 수 없습니다."));

            // 재고 확인 및 차감
            if (productSize.getStock() < itemDto.getQuantity()) {
                throw new IllegalStateException("재고가 부족합니다.");
            }
            productSize.setStock(productSize.getStock() - itemDto.getQuantity());

            OrderItem orderItem = OrderItem.builder()
                    .orders(order)
                    .product(product)
                    .productSizeEntity(productSize)
                    .quantity(itemDto.getQuantity())
                    .price(itemDto.getPrice())
                    .productName(itemDto.getProductName())
                    .productSize(itemDto.getSize())
                    .build();

            order.addOrderItem(orderItem);
        }
    }
}
