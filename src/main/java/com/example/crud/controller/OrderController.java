package com.example.crud.controller;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.service.CartService;
import com.example.crud.data.order.dto.OrderDto;
import com.example.crud.data.order.dto.OrderItemDto;
import com.example.crud.data.order.dto.OrderPreparationDto;
import com.example.crud.data.order.service.OrderService;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import com.example.crud.entity.Orders;
import com.example.crud.entity.ProductOption;
import com.example.crud.enums.OrderType;
import com.example.crud.enums.PaymentMethodType;
import com.example.crud.repository.ProductOptionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;
    private final ProductService productService;
    private final ProductOptionRepository productOptionRepository;

    // 장바구니에서 주문
    @GetMapping("/checkout")
    public String checkoutFromCart(@RequestParam("items") List<Long> cartItemIds, Model model) {
        if (cartItemIds.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품을 선택해주세요.");
        }

        // 선택된 장바구니 아이템만 조회
        CartDto selectedCart = cartService.getCartItems(cartItemIds);
        List<OrderItemDto> orderItems = orderService.prepareOrderItems(selectedCart.getCartItems());
        OrderPreparationDto orderPrep = orderService.prepareOrder(orderItems);

        setOrderModelAttributes(model, orderPrep, OrderType.CART);
        model.addAttribute("cartItemIds", cartItemIds); // 장바구니 아이템 ID 보존
        return "fragments/productBuy";
    }

    // 상품 상세페이지에서 직접 주문
    @GetMapping("/checkout/direct")
    public String checkoutDirect(
            @RequestParam("productId") Long productId,
            @RequestParam("color") String color,  // 추가
            @RequestParam("size") String size,
            @RequestParam("quantity") int quantity,
            Model model) {

        ProductOption productOption = productOptionRepository
                .findByProduct_NumberAndColorAndSize(productId, color, size)
                .orElseThrow(() -> new IllegalArgumentException("해당 옵션을 찾을 수 없습니다."));

        // 재고 확인
        if (productOption.getStock() < quantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }

        ProductResponseDto product = productService.getProductById(productId);
        OrderPreparationDto orderPrep = orderService.prepareDirectOrder(product, color, size, quantity);

        setOrderModelAttributes(model, orderPrep, OrderType.DIRECT);
        return "fragments/productBuy";
    }

    // 주문 처리
    @PostMapping("/place")
    @ResponseBody
    public Map<String, Object> placeOrder(@Valid @RequestBody OrderDto orderDto) {
        Orders order = orderService.createOrder(orderDto);

        // 장바구니 주문인 경우 장바구니에서 제거
        if (orderDto.getOrderType() == OrderType.CART) {
            cartService.removeOrderedItems(orderDto.getCartItemIds());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", order.getId());
        response.put("finalAmount", order.getTotalAmount() + order.getDeliveryFee());
        // 주문 생성 후 PaymentController의 결제 처리 엔드포인트로 리다이렉트
        return response;
    }

    // 주문 완료 페이지
    @GetMapping("/complete/{orderId}")
    public String orderComplete(@PathVariable Long orderId, Model model) {
        Orders order = orderService.getOrder(orderId);
        model.addAttribute("order", order);
        return "fragments/orderComplete";
    }

    private void setOrderModelAttributes(Model model, OrderPreparationDto orderPrep, OrderType orderType) {
        model.addAttribute("orderItems", orderPrep.getOrderItems());
        model.addAttribute("totalAmount", orderPrep.getTotalAmount());
        model.addAttribute("deliveryFee", orderPrep.getDeliveryFee());
        model.addAttribute("finalAmount", orderPrep.getFinalAmount());
        model.addAttribute("orderType", orderType);
        model.addAttribute("paymentMethods", PaymentMethodType.values());
    }

    // 에러 처리 추가
    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, MethodArgumentNotValidException.class})
    @ResponseBody
    public Map<String, Object> handleException(Exception e) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", 500);
        errorResponse.put("error", e.getMessage());
        return errorResponse;
    }

    // 추가: 유효성 검사 실패 (MethodArgumentNotValidException) 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleValidationExceptions(MethodArgumentNotValidException ex, RedirectAttributes redirectAttributes) {
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        redirectAttributes.addFlashAttribute("error", errorMessage);
        return "redirect:/error"; // 또는 에러 페이지로 이동
    }

}
