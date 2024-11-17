package com.example.crud.data.order.dto;

import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderItemDto {
    private Long productId;
    private String productName;
    private String size;
    private int price;
    private int quantity;
    private String imageUrl;
    private boolean stockAvailable; // 재고 있음 여부

    private int calculateAmount() {
        return price * quantity;
    }
}
