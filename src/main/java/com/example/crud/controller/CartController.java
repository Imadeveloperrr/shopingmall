package com.example.crud.controller;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cart")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);
    private final CartService cartService;

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
            String size = payload.get("size").toString();
            int quantity = Integer.parseInt(payload.get("quantity").toString());

            cartService.addCartItem(productId, size, quantity);
            return ResponseEntity.ok("success");
        } catch (Exception e) {
            log.error("Error adding to cart", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
