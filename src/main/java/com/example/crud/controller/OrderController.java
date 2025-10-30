package com.example.crud.controller;

import com.example.crud.data.cart.dto.checkout.CartCheckoutItem;
import com.example.crud.data.cart.service.checkout.CartCheckoutService;
import com.example.crud.data.cart.service.remove.RemoveCartItemService;
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
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;
    private final CartCheckoutService cartCheckoutService;
    private final RemoveCartItemService removeCartItemService;
    private final ProductService productService;
    private final ProductOptionRepository productOptionRepository;

    @GetMapping("/checkout")
    public String checkoutFromCart(@RequestParam("items") List<Long> cartItemIds, Model model) {
        if (cartItemIds == null || cartItemIds.isEmpty()) {
            throw new IllegalArgumentException("주문할 상품을 선택해주세요.");
        }

        List<CartCheckoutItem> checkoutItems = cartCheckoutService.prepareCheckoutItems(cartItemIds);
        List<OrderItemDto> orderItems = orderService.prepareOrderItems(checkoutItems);
        OrderPreparationDto orderPrep = orderService.prepareOrder(orderItems);

        setOrderModelAttributes(model, orderPrep, OrderType.CART);
        model.addAttribute("cartItemIds", cartItemIds);
        return "fragments/productBuy";
    }

    @GetMapping("/checkout/direct")
    public String checkoutDirect(
        @RequestParam("productId") Long productId,
        @RequestParam("color") String color,
        @RequestParam("size") String size,
        @RequestParam("quantity") int quantity,
        Model model
    ) {
        ProductOption productOption = productOptionRepository
            .findByProduct_NumberAndColorAndSize(productId, color, size)
            .orElseThrow(() -> new IllegalArgumentException("해당 옵션을 찾을 수 없습니다."));

        if (productOption.getStock() < quantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }

        ProductResponseDto product = productService.getProductById(productId);
        OrderPreparationDto orderPrep = orderService.prepareDirectOrder(product, color, size, quantity);

        setOrderModelAttributes(model, orderPrep, OrderType.DIRECT);
        return "fragments/productBuy";
    }

    @PostMapping("/place")
    @ResponseBody
    public Map<String, Object> placeOrder(@Valid @RequestBody OrderDto orderDto) {
        Orders order = orderService.createOrder(orderDto);

        if (orderDto.getOrderType() == OrderType.CART) {
            removeCartItemService.removeOrderedItems(orderDto.getCartItemIds());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("id", order.getId());
        response.put("finalAmount", order.getTotalAmount() + order.getDeliveryFee());
        return response;
    }

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

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class, MethodArgumentNotValidException.class})
    @ResponseBody
    public Map<String, Object> handleException(Exception e) {
        Map<String, Object> errorResponse = new HashMap<>();
        errorResponse.put("timestamp", LocalDateTime.now());
        errorResponse.put("status", 500);
        errorResponse.put("error", e.getMessage());
        return errorResponse;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public String handleValidationExceptions(MethodArgumentNotValidException ex, RedirectAttributes redirectAttributes) {
        String errorMessage = ex.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        redirectAttributes.addFlashAttribute("error", errorMessage);
        return "redirect:/error";
    }
}
