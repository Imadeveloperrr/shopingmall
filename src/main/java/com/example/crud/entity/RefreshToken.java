package com.example.crud.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.*;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Builder
public class RefreshToken {

    @Id
    @Column(name = "refresh_key")
    private String key; // 회원의 Email or ID

    @Column(nullable = false)
    private String token;

    public void updateToken(String newToken) {
        this.token = newToken;
    }
}
