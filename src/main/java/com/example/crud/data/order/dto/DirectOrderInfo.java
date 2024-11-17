package com.example.crud.data.order.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DirectOrderInfo {
    private Long productId;
    private String size;
    private int quantity;
}