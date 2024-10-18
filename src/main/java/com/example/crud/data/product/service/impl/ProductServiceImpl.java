package com.example.crud.data.product.service.impl;

import com.example.crud.data.product.dto.ProductDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import com.example.crud.entity.Member;
import com.example.crud.entity.Product;
import com.example.crud.mapper.ProductMapper;
import com.example.crud.repository.MemberRepository;
import com.example.crud.repository.ProductRepository;
import com.fasterxml.jackson.databind.util.BeanUtil;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.google.firebase.cloud.StorageClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final MemberRepository memberRepository;
    private final ProductMapper productMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProducts() {
        try {
            //List<Product> products = productRepository.findAll();
            List<Product> products = productMapper.findAllProducts();

            return products.stream()
                    .map(this::convertToProductResponseDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Error occurred while fetching products: {}", e.getMessage(), e);
            throw e;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProductsByMemberId(Long memberId) {
        //List<Product> product = productRepository.findByMemberNumber(memberId);
        List<Product> product = productMapper.findProductsByMemberId(memberId);
        return product.stream()
                .map(this::convertToProductResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getMyProducts() {
        Member member = getAuthenticatedUser();

        List<Product> products = productMapper.findProductsByMemberId(member.getNumber());
        return products.stream()
                .map(this::convertToProductResponseDTO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public ProductResponseDto getAddProduct(ProductDto productDto, MultipartFile image) throws IOException {
        String imageUrl = "";
        try {
            Member member = getAuthenticatedUser();
            imageUrl = uploadImageToFirebase(image);

            Product product = converToProductEntity(productDto, member);
            product.setImageUrl(imageUrl);
            productRepository.save(product);
            return convertToProductResponseDTO(product);
        } catch (Exception e) {
            if(!image.isEmpty()){
                deletedImageFromFirebase(imageUrl);
            }
            throw e;
        }
    }

    @Override
    public ProductResponseDto getUpdateProduct(ProductDto productDto, MultipartFile image) throws IOException {
        Member member = getAuthenticatedUser();

        Product product = productRepository.findById(productDto.getNumber())
                .orElseThrow(() -> new NoSuchElementException("ERROR : 없는 상품입니다."));
        deletedImageFromFirebase(product.getImageUrl());

        String imageUrl = uploadImageToFirebase(image);

        Product responseProduct = converToProductEntity(productDto, member);
        responseProduct.setImageUrl(imageUrl);
        productRepository.save(responseProduct);

        return convertToProductResponseDTO(product);
    }

    @Override
    public void getDeleteProduct(Long id) throws IOException {
        Member member = getAuthenticatedUser();

        Product product = productMapper.findProductById(id);
        if (product == null)
            throw new NoSuchElementException("ERROR  : 없는 상품 번호 입니다.");

        deletedImageFromFirebase(product.getImageUrl());
        productRepository.delete(product);
    }

    @Override
    public ProductResponseDto getProductById(Long id) {
        Member member = getAuthenticatedUser();

        Product product = productMapper.findProductById(id);
        if (product == null)
            throw new NoSuchElementException("ERROR : 없는 상품 번호 입니다.");

        ProductResponseDto productResponseDto = convertToProductResponseDTO(product);
        productResponseDto.setPermission(Objects.equals(member.getEmail(), product.getMemberEmail()));
        return productResponseDto;
    }

    @Override
    public ProductResponseDto getProductByName(String name) {
        return null;
    }

    private Member getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new NoSuchElementException("ERROR : is not Authenticated User");
        }
        String userEmail = authentication.getName();
        return memberRepository.findByEmail(userEmail).orElseThrow(() -> new NoSuchElementException("ERROR : Unknown User"));
    }

    private void deletedImageFromFirebase(String imageUrl) throws IOException {
        String fileName = imageUrl.substring(imageUrl.lastIndexOf('/') + 1, imageUrl.indexOf("?"));
        Storage storage = StorageClient.getInstance().bucket().getStorage();
        BlobId blobId = BlobId.of("webproject-83837.appspot.com", fileName);

        storage.delete(blobId);
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
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
        productResponseDto.setPrice(formatter.format(product.getPrice()) + "원");
        productResponseDto.setDescription(product.getDescription().replace("\n", "<br>"));
        return productResponseDto;
    }

    private Product converToProductEntity(ProductDto productDto, Member member) {
        Product product = new Product();
        BeanUtils.copyProperties(productDto, product);
        product.setMember(member);
        return product;
    }
}
