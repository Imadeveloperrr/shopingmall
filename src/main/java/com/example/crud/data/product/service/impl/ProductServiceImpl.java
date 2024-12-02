package com.example.crud.data.product.service.impl;

import com.example.crud.data.product.dto.ProductDto;
import com.example.crud.data.product.dto.ProductOptionDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import com.example.crud.entity.Member;
import com.example.crud.entity.Product;
import com.example.crud.entity.ProductOption;
import com.example.crud.mapper.ProductMapper;
import com.example.crud.repository.MemberRepository;
import com.example.crud.repository.ProductOptionRepository;
import com.example.crud.repository.ProductRepository;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
    private final ProductOptionRepository productOptionRepository;

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

            // ProductOption 엔티티 생성 및 설정
            List<ProductOption> productOptionList = new ArrayList<>();
            if (productDto.getProductOptions() != null) {
                for (ProductOptionDto optionDto : productDto.getProductOptions()) {
                    ProductOption productOption = ProductOption.builder()
                            .color(optionDto.getColor())
                            .size(optionDto.getSize())
                            .stock(optionDto.getStock())
                            .product(product)
                            .build();
                    productOptionList.add(productOption);
                }
                product.setProductOptions(productOptionList);
            }

            Product savedProduct = productRepository.save(product);
            return convertToProductResponseDTO(savedProduct);
        } catch (Exception e) {
            if (image != null && !image.isEmpty()) {
                deletedImageFromFirebase(imageUrl);
            }
            throw e;
        }
    }

    @Override
    @Transactional
    public ProductResponseDto getUpdateProduct(ProductDto productDto, MultipartFile image) throws IOException {
        Member member = getAuthenticatedUser();
        Product existingProduct = productRepository.findById(productDto.getNumber())
                .orElseThrow(() -> new NoSuchElementException("ERROR : 존재하지 않는 상품입니다."));

        // 1. 이미지 처리
        String imageUrl = handleImageUpdate(image, existingProduct);

        // 2. 기본 정보 업데이트
        updateProductBasicInfo(existingProduct, productDto);

        // 이미지 URL 설정
        existingProduct.setImageUrl(imageUrl);

        // 3. 옵션 처리
        updateProductOptions(existingProduct, productDto.getProductOptions());

        Product savedProduct = productRepository.save(existingProduct);
        return convertToProductResponseDTO(savedProduct);
    }

    // 이미지 처리 메서드
    private String handleImageUpdate(MultipartFile image, Product existingProduct) throws IOException {
        if (image == null || image.isEmpty()) {
            return existingProduct.getImageUrl();
        }

        if (existingProduct.getImageUrl() != null) {
            deletedImageFromFirebase(existingProduct.getImageUrl());
        }
        return uploadImageToFirebase(image);
    }

    // 기본 정보 업데이트 메서드
    private void updateProductBasicInfo(Product product, ProductDto productDto) {
        product.setName(productDto.getName());
        product.setBrand(productDto.getBrand());
        product.setPrice(productDto.getPrice());
        product.setIntro(productDto.getIntro());
        product.setDescription(productDto.getDescription());
        product.setCategory(productDto.getCategory());
    }

    // 옵션 업데이트 메서드
    private void updateProductOptions(Product product, List<ProductOptionDto> newOptions) {
        if (newOptions == null) return;

        List<ProductOption> existingOptions = product.getProductOptions();

        // 기존 옵션 모두 제거 (orphanRemoval로 자동 삭제됨)
        existingOptions.clear();

        // 새로운 옵션들 추가
        for (ProductOptionDto optionDto : newOptions) {
            ProductOption newOption = ProductOption.builder()
                    .color(optionDto.getColor())
                    .size(optionDto.getSize())
                    .stock(optionDto.getStock())
                    .build();
            product.addProductOption(newOption);  // 양방향 관계 설정
        }
    }

    @Override
    public void getDeleteProduct(Long id) throws IOException {
        Member member = getAuthenticatedUser();

        Product product = productMapper.findProductByNumber(id);
        if (product == null)
            throw new NoSuchElementException("ERROR  : 없는 상품 번호 입니다.");

        deletedImageFromFirebase(product.getImageUrl());
        productRepository.delete(product);
    }

    @Override
    public ProductResponseDto getProductById(Long id) {
        Member member = getAuthenticatedUser();

        Product product = productMapper.findProductByNumber(id);
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
        String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString());

        return "https://firebasestorage.googleapis.com/v0/b/" + "webproject-83837.appspot.com" + "/o/" + encodedFileName + "?alt=media";
    }

    @Override
    public List<ProductOptionDto> getProductOptions(Long productId) {
        List<ProductOption> options = productOptionRepository.findByProduct_Number(productId);

        return options.stream()
                .map(option -> ProductOptionDto.builder()
                        .id(option.getId())
                        .color(option.getColor())
                        .size(option.getSize())
                        .stock(option.getStock())
                        .build())
                .collect(Collectors.toList());
    }

    private ProductResponseDto convertToProductResponseDTO(Product product) {
        ProductResponseDto productResponseDto = new ProductResponseDto();
        BeanUtils.copyProperties(product, productResponseDto);
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.KOREA);
        productResponseDto.setPrice(formatter.format(product.getPrice()) + "원");
        productResponseDto.setDescription(product.getDescription().replace("\n", "<br>"));

        // ProductOption 정보 변환
        if (product.getProductOptions() != null) {
            List<ProductOptionDto> optionDtos = product.getProductOptions().stream()
                    .map(option -> ProductOptionDto.builder()
                            .id(option.getId())
                            .color(option.getColor())
                            .size(option.getSize())
                            .stock(option.getStock())
                            .build())
                    .collect(Collectors.toList());
            productResponseDto.setProductOptions(optionDtos);
        }

        return productResponseDto;
    }

    private Product converToProductEntity(ProductDto productDto, Member member) {
        Product product = new Product();
        BeanUtils.copyProperties(productDto, product);
        product.setMember(member);
        return product;
    }
}
