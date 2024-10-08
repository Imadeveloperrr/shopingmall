package com.example.crud.controller;

import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.data.product.dto.ProductDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import com.example.crud.entity.Product;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Controller
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final ProductService productService;
    private final MemberService memberService;

    @GetMapping
    public String product() {
        return "fragments/productAdd";
    }

    @GetMapping("/buy")
    public String productBuy(@RequestParam("id") Long productId, Model model) {
        ProductResponseDto productResponseDto = productService.getProductById(productId);
        model.addAttribute("product", productResponseDto);
        return "fragments/productBuy";
    }

    @PostMapping("/add")
    public ResponseEntity<?> addProduct(@ModelAttribute ProductDto productDto, @RequestParam("imageUrl") MultipartFile file) {
        try {
            log.info("Received product: {}", productDto);
            if (file.isEmpty()) {
                log.error("File is empty");
                return ResponseEntity.badRequest().body("File is empty");
            }
            ProductResponseDto productResponseDto = productService.getAddProduct(productDto, file);
            return ResponseEntity.ok().body(productResponseDto);
        } catch (Exception e) {
            log.error("Error while adding product", e);
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id) throws IOException {
        productService.getDeleteProduct(id);
        return "fragments/mypage";
    }

    @GetMapping("/update/{id}")
    public String update(@PathVariable Long id, Model model) {
        ProductResponseDto productResponseDto = productService.getProductById(id);
        model.addAttribute("product", productResponseDto);
        return "fragments/productUpdate";
    }

    @PostMapping("/update")
    public ResponseEntity<?> updateProduct(@ModelAttribute ProductDto productDto, @RequestParam("imageUrl") MultipartFile file) {
        try {
            log.info("Received product: {}", productDto);
            if (file.isEmpty()) {
                log.error("File is empty");
                return ResponseEntity.badRequest().body("File is Empty");
            }
            ProductResponseDto productResponseDto = productService.getUpdateProduct(productDto, file);
            return ResponseEntity.ok().body(productResponseDto);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ProductResponseDto productResponseDto = productService.getProductById(id);
        model.addAttribute("product", productResponseDto);

        MemberResponseDto memberResponseDto = memberService.getMember();
        model.addAttribute("member", memberResponseDto);
        return "fragments/productDetail";
    }
}
