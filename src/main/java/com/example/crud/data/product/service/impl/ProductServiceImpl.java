package com.example.crud.data.product.service.impl;

import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import com.example.crud.entity.Member;
import com.example.crud.entity.Product;
import com.example.crud.repository.MemberRepository;
import com.example.crud.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public List<Product> getProducts() {
        return productRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsByMemberId(Long memberId) {
        return productRepository.findByMemberId(memberId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getMyProducts() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String useremail = authentication.getName();
        Member member = memberRepository.findByEmail(useremail)
                .orElseThrow(() -> new NoSuchElementException("ERROR : 존재 하지 않는 사용자"));
        return productRepository.findByMemberId(member.getNumber());
    }
}
