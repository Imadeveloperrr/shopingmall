package com.example.crud.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JwtTokenProvider {
    private final Key key;
    private final long accessTokenValidityInMilliseconds;
    private final long refreshTokenValidityInMilliseconds;

    private static final long ACCESS_TOKEN_EXPIRE_TIME = 1000 * 60 * 30;            // 30분
    private static final long REFRESH_TOKEN_EXPIRE_TIME = 1000 * 60 * 60 * 24 * 7;  // 7일

    private static final long ACCESS_TOKEN_EXPIRE_TIME_REMEMBER_ME = 1000 * 60 * 60 * 24 * 7; // 7일
    private static final long REFRESH_TOKEN_EXPIRE_TIME_REMEMBER_ME = 1000 * 60 * 60 * 24 * 14; // 14일


    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-token-validity-in-seconds}") long accessTokenValidityInSeconds,
            @Value("${jwt.refresh-token-validity-in-seconds}") long refreshTokenValidityInSeconds) {

        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenValidityInMilliseconds = accessTokenValidityInSeconds * 1000;
        this.refreshTokenValidityInMilliseconds = refreshTokenValidityInSeconds * 1000;
    }

    // Member 정보를 가지고 AccessToken, RefreshToken을 생성하는 메서드.
    public JwtToken generateToken(Authentication authentication, boolean rememberMe) {
        String authorities = authentication.getAuthorities().stream()// 권한 컬렉션을 가져와서 stream으로 변환
                .map(GrantedAuthority::getAuthority) // 각 권한 객체의 getAuthority 메서드를 호출하여 권한이름이름 가져오기
                .collect(Collectors.joining(",")); // 스트림의 각 요소를 , 구분하여 결합

        long now = (new Date()).getTime();
        Date accessTokenExpiresIn;
        Date refreshTokenExpiresIn;

        if (rememberMe) {
            accessTokenExpiresIn = new Date(now + ACCESS_TOKEN_EXPIRE_TIME_REMEMBER_ME);
            refreshTokenExpiresIn = new Date(now + REFRESH_TOKEN_EXPIRE_TIME_REMEMBER_ME);
        } else {
            accessTokenExpiresIn = new Date(now + ACCESS_TOKEN_EXPIRE_TIME);
            refreshTokenExpiresIn = new Date(now + REFRESH_TOKEN_EXPIRE_TIME);
        }

        // Access Token 생성
        String accessToken = Jwts.builder()
                .setSubject(authentication.getName())
                .claim("auth", authorities)
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();

        // Refresh Token 생성
        String refreshToken = Jwts.builder()
                .setSubject(authentication.getName())
                .setExpiration(refreshTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();


        return JwtToken.builder()
                .grantType("Bearer")
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    // Jwt 토큰을 복호화하여 토큰에 들어있는 정보를 꺼내는 메서드.
    public Authentication getAuthentication(String accessToken) {

        Claims claims = parseClaims(accessToken);

        if (claims.get("auth") == null) {
            throw new RuntimeException("권한 정보가 없는 토큰입니다.");
        }

        Collection<? extends GrantedAuthority> authorities = Arrays.stream(claims.get("auth").toString().split(","))
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());

        // UserDetails 객체를 만들어서 Authentication return
        // UserDetails : interface , User : UserDetails를 구현한 class
        User principal = new User(claims.getSubject(), "", authorities);
        return new UsernamePasswordAuthenticationToken(principal, "", authorities);
    }

    // Token 정보를 검증하는 Method
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder() // jwt 파싱을 위한 빌더를 생성
                    .setSigningKey(key) // 서명검증을 위한 키 설정.
                    .build() // JwtParser 인스턴스를 생성
                    .parseClaimsJws(token); // 토큰을 파싱하고 클레임을 검증, 토큰이 유효한지, 올바르게 서명되었는지, 만료되지 않았는지 확인
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.info("Invalid JWT Token", e);
        } catch (ExpiredJwtException e) {
            log.info("Expired JWT Token");
        } catch (UnsupportedJwtException e) {
            log.info("Unsupported JWT Token");
        } catch (IllegalArgumentException e) {
            log.info("JWT claims string is empty");
        }
        return false;
    }

    // accessToken
    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(accessToken) // 토큰을 파싱하고 서명이 유효한지 구조가 올바른지 등 검증
                    .getBody(); // Jws<Claims>에서 Claims 부분만 추출
        } catch (ExpiredJwtException e) {
            return e.getClaims();
        }
    }
}

