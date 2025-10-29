package com.example.crud.controller;

import com.example.crud.data.cart.dto.*;
import com.example.crud.data.cart.dto.request.AddCartItemRequest;
import com.example.crud.data.cart.dto.request.UpdateCartItemOptionRequest;
import com.example.crud.data.cart.dto.request.UpdateCartItemQuantityRequest;
import com.example.crud.data.cart.service.add.AddCartItemService;
import com.example.crud.data.cart.service.clear.ClearCartService;
import com.example.crud.data.cart.service.find.CartFindService;
import com.example.crud.data.cart.service.remove.RemoveCartItemService;
import com.example.crud.data.cart.service.update.UpdateCartItemService;
import com.example.crud.data.product.dto.ProductOptionDto;
import com.example.crud.data.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cart")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);
    private final CartFindService cartFindService;
    private final AddCartItemService addCartItemService;
    private final RemoveCartItemService removeCartItemService;
    private final UpdateCartItemService updateCartItemService;
    private final ClearCartService clearCartService;
    private final ProductService productService;

    @GetMapping
    public String cart(Model model) {
        CartDto cartDto = cartFindService.getCartByAuthenticateMember();
        model.addAttribute("cart", cartDto);
        return "fragments/productCart";
    }

    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<String> addToCart(@Valid @RequestBody AddCartItemRequest request) {
        try {
            addCartItemService.addCartItem(
                    request.productId(),
                    request.color(),
                    request.size(),
                    request.quantity()
            );
            return ResponseEntity.ok("장바구니에 추가되었습니다.");
        } catch (Exception e) {
            log.error("장바구니 추가 실패", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/update/{itemId}")
    @ResponseBody
    public ResponseEntity<String> updateCartItem(
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemQuantityRequest request
    ) {
        try {
            updateCartItemService.updateQuantity(itemId, request.quantity());
            return ResponseEntity.ok("수량이 변경되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/remove/{itemId}")
    @ResponseBody
    public ResponseEntity<String> removeCartItem(@PathVariable Long itemId) {
        try {
            removeCartItemService.removeCartItem(itemId);
            return ResponseEntity.ok("상품이 삭제되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/update-option/{itemId}")
    @ResponseBody
    public ResponseEntity<String> updateCartItemOption(
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateCartItemOptionRequest request
    ) {
        try {
            updateCartItemService.updateOption(itemId, request.color(), request.size());
            return ResponseEntity.ok("옵션이 변경되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/clear")
    @ResponseBody
    public ResponseEntity<String> clearCart() {
        try {
            clearCartService.clearCart();
            return ResponseEntity.ok("장바구니가 비워졌습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/product/options/{productId}")
    @ResponseBody
    public ResponseEntity<List<ProductOptionDto>> getProductOptions(@PathVariable Long productId) {
        try {
            List<ProductOptionDto> options = productService.getProductOptions(productId);
            return ResponseEntity.ok(options);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(null);
        }
    }
}
