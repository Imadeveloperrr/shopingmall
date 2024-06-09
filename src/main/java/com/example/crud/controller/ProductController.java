package com.example.crud.controller;

import com.example.crud.data.product.dto.ProductDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Controller
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final ProductService productService;

    @GetMapping
    public String product() {
        return "fragments/productAdd";
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
}
