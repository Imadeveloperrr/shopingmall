package com.example.crud.data.product.service.impl;

import com.example.crud.ai.embedding.application.EmbeddingService;
import com.example.crud.ai.embedding.event.ProductCreatedEvent;
import org.springframework.context.ApplicationEventPublisher;
import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.data.product.dto.ProductDto;
import com.example.crud.data.product.dto.ProductOptionDto;
import com.example.crud.data.product.dto.ProductResponseDto;
import com.example.crud.data.product.service.ProductService;
import com.example.crud.entity.Member;
import com.example.crud.entity.Product;
import com.example.crud.entity.ProductOption;
import com.example.crud.common.mapper.ProductMapper;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.stream.Collectors;
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
    private final EmbeddingService embeddingService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<ProductResponseDto> getProducts() {
        try {
            List<Product> products = productMapper.findAllProducts();

            return products.stream()
                    .map(this::convertToProductResponseDTO)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Failed to fetch products: {}", e.getMessage());
            throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR);
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

    // 상품추가.
    @Override
    @Transactional
    public ProductResponseDto getAddProduct(ProductDto productDto, MultipartFile image) {
        String imageUrl = null;  // null로 초기화
        try {
            Member member = getAuthenticatedUser();

            // 이미지가 있을 때만 업로드
            if (image != null && !image.isEmpty()) {
                try {
                    imageUrl = uploadImageToFirebase(image);
                } catch (IOException e) {
                    log.error("Failed to upload image: {}", e.getMessage());
                    throw new BaseException(ErrorCode.IMAGE_UPLOAD_FAILED);
                }
            }

            Product product = convertToProductEntity(productDto, member);
            product.setImageUrl(imageUrl);
            product.setDescriptionVector(null); // 임시로 벡터 null 설정

            // ProductOption 엔티티 생성 및 설정
            if (productDto.getProductOptions() != null && !productDto.getProductOptions().isEmpty()) {
                List<ProductOption> productOptionList = new ArrayList<>();
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

            try {
                Product savedProduct = productRepository.save(product);
                // 트랜잭션 커밋 후 임베딩 생성하기 위한 이벤트 발행
                eventPublisher.publishEvent(new ProductCreatedEvent(savedProduct.getNumber()));

                return convertToProductResponseDTO(savedProduct);
            } catch (Exception e) {
                // 상품 저장 실패 시 업로드된 이미지 삭제
                if (imageUrl != null) {
                    try {
                        deletedImageFromFirebase(imageUrl);
                    } catch (IOException ex) {
                        log.error("Failed to delete image after product save failure: {}", ex.getMessage());
                    }
                }
                throw new BaseException(ErrorCode.PRODUCT_UPLOAD_FAILED);
            }
        } catch (BaseException e) {
            // BaseException은 그대로 전파
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error while adding product: {}", e.getMessage());
            // 예상치 못한 에러 발생 시 이미지 삭제
            if (imageUrl != null) {
                try {
                    deletedImageFromFirebase(imageUrl);
                } catch (IOException ex) {
                    log.error("Failed to delete image after unexpected error: {}", ex.getMessage());
                }
            }
            throw new BaseException(ErrorCode.PRODUCT_UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional
    public ProductResponseDto getUpdateProduct(ProductDto productDto, MultipartFile image) {
        try {
            Member member = getAuthenticatedUser();
            Product existingProduct = productRepository.findById(productDto.getNumber())
                    .orElseThrow(() -> new BaseException(ErrorCode.PRODUCT_NOT_FOUND));

            // 권한 체크
            if (!existingProduct.getMemberEmail().equals(member.getEmail())) {
                throw new BaseException(ErrorCode.UNAUTHORIZED_PRODUCT_ACCESS);
            }

            // 1. 이미지 처리
            String imageUrl = handleImageUpdate(image, existingProduct);

            // 2. 기본 정보 업데이트
            updateProductBasicInfo(existingProduct, productDto);

            // 이미지 URL 설정
            existingProduct.setImageUrl(imageUrl);

            // 3. 옵션 처리
            updateProductOptions(existingProduct, productDto.getProductOptions());

            Product savedProduct = productRepository.save(existingProduct);

            // 상품 업데이트 후 임베딩 재생성
            embeddingService.createAndSaveEmbedding(savedProduct.getNumber());

            return convertToProductResponseDTO(savedProduct);
        } catch (BaseException e) {
            throw e;
        } catch (IOException e) {
            log.error("Failed to update product image: {}", e.getMessage());
            throw new BaseException(ErrorCode.IMAGE_UPLOAD_FAILED);
        } catch (Exception e) {
            log.error("Failed to update product: {}", e.getMessage());
            throw new BaseException(ErrorCode.PRODUCT_UPDATE_FAILED);
        }
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

        // 새 옵션들을 Map으로 변환 (color+size를 key로)
        Map<String, ProductOptionDto> newOptionsMap = newOptions.stream()
                .collect(Collectors.toMap(
                        dto -> dto.getColor() + "_" + dto.getSize(),
                        dto -> dto,
                        (dto1, dto2) -> dto1  // 중복 시 첫번째 것 사용
                ));

        // 기존 옵션들을 Map으로 변환
        Map<String, ProductOption> existingOptionsMap = new HashMap<>();
        Iterator<ProductOption> iterator = existingOptions.iterator();

        while (iterator.hasNext()) {
            ProductOption existingOption = iterator.next();
            String key = existingOption.getColor() + "_" + existingOption.getSize();

            if (newOptionsMap.containsKey(key)) {
                // 기존 옵션을 업데이트
                ProductOptionDto dto = newOptionsMap.get(key);
                existingOption.setStock(dto.getStock());
                existingOptionsMap.put(key, existingOption);
            } else {
                // 새 옵션에 없으면 삭제
                iterator.remove();
            }
        }

        // 새로 추가할 옵션들 처리
        for (Map.Entry<String, ProductOptionDto> entry : newOptionsMap.entrySet()) {
            if (!existingOptionsMap.containsKey(entry.getKey())) {
                ProductOptionDto optionDto = entry.getValue();
                ProductOption newOption = ProductOption.builder()
                        .color(optionDto.getColor())
                        .size(optionDto.getSize())
                        .stock(optionDto.getStock())
                        .build();
                product.addProductOption(newOption);
            }
        }
    }

    @Override
    public void getDeleteProduct(Long id) {
        try {
            Member member = getAuthenticatedUser();
            Product product = productMapper.findProductByNumber(id);

            if (product == null)
                throw new BaseException(ErrorCode.PRODUCT_NOT_FOUND);

            if (!product.getMemberEmail().equals(member.getEmail())) {
                throw new BaseException(ErrorCode.UNAUTHORIZED_PRODUCT_ACCESS);
            }

            if (product.getImageUrl() != null) {
                deletedImageFromFirebase(product.getImageUrl());
            }

            productRepository.delete(product);
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete product: {}", e.getMessage());
            throw new BaseException(ErrorCode.PRODUCT_DELETE_FAILED);
        }
    }

    @Override
    public ProductResponseDto getProductById(Long id) {
        try {
            Member member = getAuthenticatedUser();
            Product product = productMapper.findProductByNumber(id);

            if (product == null) {
                throw new BaseException(ErrorCode.PRODUCT_NOT_FOUND);
            }
            ProductResponseDto productResponseDto = convertToProductResponseDTO(product);
            productResponseDto.setPermission(Objects.equals(member.getEmail(), product.getMemberEmail()));
            return productResponseDto;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch product: {}", e.getMessage());
            throw new BaseException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ProductResponseDto getProductByName(String name) {
        return null;
    }

    private Member getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() ||
                authentication instanceof AnonymousAuthenticationToken) {
            throw new BaseException(ErrorCode.INVALID_CREDENTIALS);
        }

        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BaseException(ErrorCode.MEMBER_NOT_FOUND));
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

        // category 필드를 수동으로 변환하여 설정
        if (product.getCategory() != null) {
            productResponseDto.setCategory(product.getCategory().name());
        }

        productResponseDto.setSubCategory(product.getSubCategory());

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

    private Product convertToProductEntity(ProductDto productDto, Member member) {
        Product product = new Product();
        BeanUtils.copyProperties(productDto, product);
        product.setMember(member);

        product.setSubCategory(productDto.getSubCategory());
        return product;
    }
}
