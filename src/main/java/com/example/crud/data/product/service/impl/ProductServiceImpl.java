package com.example.crud.data.product.service.impl;

import com.example.crud.data.product.dto.ProductDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import com.example.crud.entity.Member;
import com.example.crud.entity.Product;
import com.example.crud.repository.MemberRepository;
import com.example.crud.repository.ProductRepository;
import com.fasterxml.jackson.databind.util.BeanUtil;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.cloud.StorageClient;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProducts() {
        List<Product> products = productRepository.findAll();
        return products.stream()
                .map(this::convertToProductResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsByMemberId(Long memberId) {
        List<Product> product = productRepository.findByMemberNumber(memberId);
        return product.stream()
                .map(this::convertToProductResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getMyProducts() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String useremail = authentication.getName();
        Member member = memberRepository.findByEmail(useremail)
                .orElseThrow(() -> new NoSuchElementException("ERROR : 존재 하지 않는 사용자"));

        List<Product> products = productRepository.findByMemberNumber(member.getNumber());
        return products.stream()
                .map(this::convertToProductResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    public ProductResponseDto getAddProduct(ProductDto productDto, MultipartFile image) throws IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String useremail = authentication.getName();
        Member member = memberRepository.findByEmail(useremail)
                .orElseThrow(() -> new NoSuchElementException("ERROR : 존재 하지 않는 사용자"));

        String imageUrl = uploadImageToFirebase(image);

        Product product = converToProductEntity(productDto, member);
        product.setImageUrl(imageUrl);
        productRepository.save(product);
        return convertToProductResponseDTO(product);
    }

    @Override
    public ProductResponseDto getUpdateProduct(ProductDto productDto) {
        return null;
    }

    @Override
    public ProductResponseDto getDeleteProduct(ProductDto productDto) {
        return null;
    }

    @Override
    public ProductResponseDto getProductById(Long id) {
        return null;
    }

    @Override
    public ProductResponseDto getProductByName(String name) {
        return null;
    }

    private String uploadImageToFirebase(MultipartFile image) throws IOException {
        Storage storage = StorageClient.getInstance().bucket().getStorage();

        String fileName = UUID.randomUUID().toString() + "-" + image.getOriginalFilename();
        BlobId blobId = BlobId.of("webproject-83837.appspot.com", fileName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType(image.getContentType()).build();

        storage.create(blobInfo, image.getBytes());

        return "https://firebasestorage.googleapis.com/v0/b/" + "webproject-83837.appspot.com" + "/o/" + fileName + "?alt=media";
    }

    private ProductResponseDto convertToProductResponseDTO(Product product) {
        ProductResponseDto productResponseDto = new ProductResponseDto();
        BeanUtils.copyProperties(product, productResponseDto);
        return productResponseDto;
    }

    private Product converToProductEntity(ProductDto productDto, Member member) {
        Product product = new Product();
        BeanUtils.copyProperties(productDto, product);
        product.setMember(member);
        return product;
    }
}
