package com.example.crud.controller;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.service.CartService;
import com.example.crud.data.product.dto.ProductOptionDto;
import com.example.crud.data.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cart")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);
    private final CartService cartService;
    private final ProductService productService;

    @GetMapping
    public String cart(Model model) {
        CartDto cartDto = cartService.getCartByAuthenticateMember();
        model.addAttribute("cart", cartDto);
        return "fragments/productCart";
    }

    @PostMapping("/add")
    @ResponseBody
    public ResponseEntity<String> addToCart(@RequestBody Map<String, Object> payload) {
        try {
            Long productId = Long.parseLong(payload.get("productId").toString());
            String color = payload.get("color").toString();  // 추가
            String size = payload.get("size").toString();
            int quantity = Integer.parseInt(payload.get("quantity").toString());

            cartService.addCartItem(productId, color, size, quantity);  // 수정
            return ResponseEntity.ok("장바구니에 추가되었습니다.");
        } catch (Exception e) {
            log.error("장바구니 추가 실패", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/update/{itemId}")
    @ResponseBody
    public ResponseEntity<String> updateCartItem(@PathVariable Long itemId, @RequestBody Map<String, Integer> payload) {
        try {
            cartService.updateCartItemQuantity(itemId, payload.get("quantity"));
            return ResponseEntity.ok("수량이 변경되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @DeleteMapping("/remove/{itemId}")
    @ResponseBody
    public ResponseEntity<String> removeCartItem(@PathVariable Long itemId) {
        try {
            cartService.removeCartItem(itemId);
            return ResponseEntity.ok("상품이 삭제되었습니다.");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/update-option/{itemId}")
    @ResponseBody
    public ResponseEntity<String> updateCartItemOption(
            @PathVariable Long itemId,
            @RequestBody Map<String, String> payload
    ) {
        try {
            String newColor = payload.get("color");
            String newSize = payload.get("size");
            cartService.updateCartItemOption(itemId, newColor, newSize);
            return ResponseEntity.ok("옵션이 변경되었습니다.");
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
