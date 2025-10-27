package com.example.crud.controller;

import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class IndexController {
    private final ProductService productService;

    @GetMapping("/")
    public String index(Model model) {
        try {
            List<ProductResponseDto> products = productService.getProducts();
            model.addAttribute("products", products);
        } catch (Exception e) {
            log.warn("Failed to load products for main page: {}", e.getMessage());
            model.addAttribute("products", new ArrayList<ProductResponseDto>());
        }
        return "index";
    }
    
    @GetMapping("/test")
    @ResponseBody
    public String test() {
        return "Test endpoint working - " + java.time.LocalDateTime.now();
    }

    @GetMapping("/login")
    public String login() {
        return "fragments/login";
    }

    @GetMapping("/ai-recommendation")
    public String aiRecommendation() {
        return "fragments/ai-recommendation";
    }

    @GetMapping("/register")
    public String register() {
        return "fragments/register";
    }
}
