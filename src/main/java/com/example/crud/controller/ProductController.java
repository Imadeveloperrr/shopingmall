package com.example.crud.controller;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.member.service.find.MemberFindService;
import com.example.crud.data.product.dto.ProductDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final ProductService productService;
    private final MemberFindService memberFindService;

    @GetMapping
    public String product() {
        return "fragments/productAdd";
    }

    @PostMapping("/add")
    public ResponseEntity<ProductResponseDto> addProduct(@ModelAttribute ProductDto productDto,
                                                         @RequestParam("imageFile") MultipartFile file) {
        log.info("Received product: {}", productDto);
        if (file == null || file.isEmpty()) {
            throw new BaseException(ErrorCode.INVALID_INPUT, "product.image.required");
        }
        ProductResponseDto response = productService.getAddProduct(productDto, file);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            productService.getDeleteProduct(id);
            redirectAttributes.addFlashAttribute("message", "상품이 성공적으로 삭제되었습니다.");
            return "redirect:/mypage";  // /mypage로 리다이렉트
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "상품 삭제 중 오류가 발생했습니다.");
            return "redirect:/mypage";
        }
    }

    @GetMapping("/update/{id}")
    public String update(@PathVariable Long id, Model model) {
        ProductResponseDto productResponseDto = productService.getProductById(id);
        model.addAttribute("product", productResponseDto);
        return "fragments/productUpdate";
    }

    @PostMapping("/update")
    public ResponseEntity<ProductResponseDto> updateProduct(
            @ModelAttribute ProductDto productDto,
            @RequestParam(value = "imageUrl", required = false) MultipartFile file) {
        log.info("Updating product with ID: {}", productDto.getNumber());
        ProductResponseDto response = productService.getUpdateProduct(productDto, file);
        log.info("Product updated successfully. ID: {}", response.getNumber());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/detail/{id}")
    public String detail(@PathVariable Long id, Model model) {
        ProductResponseDto productResponseDto = productService.getProductById(id);
        model.addAttribute("product", productResponseDto);

        MemberResponseDto memberResponseDto = memberFindService.getCurrentMember();
        model.addAttribute("member", memberResponseDto);
        return "fragments/productDetail";
    }
}
