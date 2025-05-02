package com.example.crud.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Builder
@Data
@AllArgsConstructor
public class JwtToken {
    private String grantType; // jwt에 대한 인증 타입 Bearer 인증 방식을 사용할거임
    private String accessToken;
    private String refreshToken;
}

