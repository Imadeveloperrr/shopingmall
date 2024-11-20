package com.example.crud.data.order.service.impl;

import com.example.crud.data.cart.dto.CartItemDto;
import com.example.crud.data.cart.service.CartService;
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

    /**
     * 장바구니 상품들을 주문 가능한 형태(OrderItemDto)로 변환
     */
    @Override
    public List<OrderItemDto> prepareOrderItems(List<CartItemDto> cartItems) {
        return cartItems.stream()
                .map(this::convertToOrderItemDto)
                .collect(Collectors.toList());
    }

    /**
     * 상품 상세페이지에서 직접 주문 시 주문 준비 정보 생성
     * 1. 단일 상품을 OrderItemDto로 변환
     * 2. 배송비 계산하여 최종 주문 준비 정보(OrderPreparationDto) 반환
     */
    @Override
    public OrderPreparationDto prepareDirectOrder(ProductResponseDto product, String size, int quantity) {
        // 사용자가 선택한 상품 정보로 OrderItemDto 생성
        OrderItemDto orderItemDto = OrderItemDto.builder()
                .productId(product.getNumber())
                .productName(product.getName())
                .price(parsePrice(product.getPrice()))
                .quantity(quantity)
                .imageUrl(product.getImageUrl())
                .size(size)
                .stockAvailable(true)
                .build();

        // 단일 상품 주문이므로 List로 변환하여 공통 주문 준비 로직 호출
        List<OrderItemDto> orderItems = List.of(orderItemDto);
        return prepareOrder(orderItems);
    }

    /**
     * 주문 준비 공통 로직
     * - 총 상품 금액 계산
     * - 배송비 계산
     * - 최종 결제 금액 계산
     */
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

    /**
     * 실제 주문 생성 처리
     * 1. 주문 기본 정보 생성 (배송지, 결제 정보 등)
     * 2. 주문 타입(장바구니/직접)에 따라 주문 상품 추가
     * 3. 재고 확인 및 차감
     * 4. 주문 정보 저장
     */
    @Override
    @Transactional
    public Orders createOrder(OrderDto orderDto) {
        // 현재 로그인한 회원 조회
        Member member = getCurrentMember();

        // 주문 기본 정보 생성
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

        // 주문 타입에 따라 상품 추가 방식 결정
        if (orderDto.getOrderType() == OrderType.CART) {
            createOrderItemsFromCart(order, orderDto.getCartItemIds());
        } else {
            createOrderItems(order, orderDto.getOrderItems());
        }

        return orderRepository.save(order);
    }

    /**
     * 장바구니 상품으로 주문 상품 생성
     * 1. 장바구니 ID로 실제 CartItem 조회
     * 2. 재고 확인
     * 3. OrderItem 생성 및 주문에 추가
     * 4. 재고 차감
     */
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

    /**
     * OrderItemDto로 주문 상품 생성 (직접 주문)
     * 1. 상품 정보 조회
     * 2. 사이즈별 재고 확인
     * 3. OrderItem 생성 및 주문에 추가
     * 4. 재고 차감
     */
    private void createOrderItems(Orders order, List<OrderItemDto> orderItemDtos) {
        for (OrderItemDto itemDto : orderItemDtos) {
            // 상품 및 사이즈 정보 조회
            Product product = productRepository.findById(itemDto.getProductId())
                    .orElseThrow(() -> new NoSuchElementException("상품을 찾을 수 없습니다."));

            ProductSize productSize = productSizeRepository.findByProduct_NumberAndSize(
                            itemDto.getProductId(), itemDto.getSize())
                    .orElseThrow(() -> new NoSuchElementException("해당 사이즈를 찾을 수 없습니다."));

            // 재고 확인
            validateStock(itemDto.getProductId(), itemDto.getSize(), itemDto.getQuantity());

            // 주문 상품 생성
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
            updateStock(productSize, itemDto.getQuantity());
        }
    }

    /**
     * 재고 검증
     * 요청 수량만큼 재고가 있는지 확인
     */
    private void validateStock(Long productId, String size, int quantity) {
        if (!checkStock(productId, size, quantity)) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
    }

    /**
     * 재고 차감 처리
     * 주문 수량만큼 재고 감소
     */
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

    /**
     * CartItem을 OrderItemDto로 변환하는 유틸리티 메서드
     */
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

    /**
     * 총 상품 금액 계산
     * 각 상품의 (가격 * 수량)의 합계
     */
    private int calculateTotalAmount(List<OrderItemDto> orderItems) {
        return orderItems.stream()
                .mapToInt(item -> item.getPrice() * item.getQuantity())
                .sum();
    }

    /**
     * 문자열 형태의 가격을 숫자로 변환
     * ex) "10,000원" -> 10000
     */
    private int parsePrice(String priceStr) {
        return Integer.parseInt(priceStr.replaceAll("[^0-9]", ""));
    }

}
