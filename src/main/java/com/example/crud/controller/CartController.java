package com.example.crud.controller;

import com.example.crud.data.cart.dto.CartDto;
import com.example.crud.data.cart.service.CartService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequiredArgsConstructor
@RequestMapping("/cart")
public class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);
    private final CartService cartService;

    @GetMapping
    public String cart(@RequestParam("memberId") Long memberId, Model model) {
        CartDto cartDto = cartService.getCartByMemberId(memberId);
        model.addAttribute("cart", cartDto);
        return "fragments/productCart";
    }

}
