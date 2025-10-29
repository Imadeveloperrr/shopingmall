package com.example.crud.data.cart.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartItemResponse(
    Long id,
    Long productId,
    String productName,
    String productSize,
    String productColor,
    Integer price,
    Integer quantity,
    String imageUrl
) {}
