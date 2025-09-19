package com.example.crud.common.security;

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
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

/**
 * JWT 토큰 생성, 검증, 파싱을 담당하는 Provider
 * Access Token과 Refresh Token의 생성 및 검증 로직 포함
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenValidityInMilliseconds;
    private final long refreshTokenValidityInMilliseconds;

    // Remember Me 기능용 토큰 만료 시간
    private static final long ACCESS_TOKEN_EXPIRE_TIME_REMEMBER_ME = 1000 * 60 * 60 * 24 * 7;  // 7일
    private static final long REFRESH_TOKEN_EXPIRE_TIME_REMEMBER_ME = 1000 * 60 * 60 * 24 * 14; // 14일


    /**
     * JwtTokenProvider 생성자
     * application.properties에서 JWT 설정 값을 주입받아 초기화
     */
    public JwtTokenProvider(
            @Value("${jwt.secret}") String secretKey,
            @Value("${jwt.access-token-validity-in-seconds}") long accessTokenValidityInSeconds,
            @Value("${jwt.refresh-token-validity-in-seconds}") long refreshTokenValidityInSeconds) {

        // Base64 디코딩하여 HMAC SHA 키 생성
        byte[] keyBytes = Decoders.BASE64.decode(secretKey);
        this.key = Keys.hmacShaKeyFor(keyBytes);

        // 초 단위를 밀리초 단위로 변환
        this.accessTokenValidityInMilliseconds = accessTokenValidityInSeconds * 1000;
        this.refreshTokenValidityInMilliseconds = refreshTokenValidityInSeconds * 1000;
    }

    /**
     * 인증 정보를 기반으로 JWT 토큰 생성
     * @param authentication 사용자 인증 정보
     * @param rememberMe 로그인 유지 여부
     * @return AccessToken과 RefreshToken이 포함된 JwtToken 객체
     */
    public JwtToken generateToken(Authentication authentication, boolean rememberMe) {
        // 사용자 권한을 콤마로 구분된 문자열로 변환
        String authorities = authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(","));

        // 토큰 만료 시간 설정 (Remember Me 여부에 따라 다르게 설정)
        long now = (new Date()).getTime();
        Date accessTokenExpiresIn;
        Date refreshTokenExpiresIn;

        if (rememberMe) {
            // Remember Me: 더 긴 만료 시간
            accessTokenExpiresIn = new Date(now + ACCESS_TOKEN_EXPIRE_TIME_REMEMBER_ME);
            refreshTokenExpiresIn = new Date(now + REFRESH_TOKEN_EXPIRE_TIME_REMEMBER_ME);
        } else {
            // 일반 로그인: 설정 파일의 만료 시간
            accessTokenExpiresIn = new Date(now + accessTokenValidityInMilliseconds);
            refreshTokenExpiresIn = new Date(now + refreshTokenValidityInMilliseconds);
        }

        // Access Token 생성 (사용자 정보 + 권한 정보 포함)
        String accessToken = Jwts.builder()
                .setSubject(authentication.getName())
                .claim("auth", authorities)
                .setExpiration(accessTokenExpiresIn)
                .signWith(key, SignatureAlgorithm.HS512)
                .compact();

        // Refresh Token 생성 (사용자 정보만 포함)
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

    /**
     * JWT 토큰을 파싱하여 Spring Security Authentication 객체로 변환
     * @param accessToken JWT Access Token
     * @return 인증 정보 객체 (실패 시 null)
     */
    public Authentication getAuthentication(String accessToken) {
        try {
            Claims claims = parseClaims(accessToken);

            // 토큰에서 권한 정보 추출
            String authClaim = claims.get("auth", String.class);
            if (authClaim == null || authClaim.trim().isEmpty()) {
                log.warn("Token contains no authorities");
                return null;
            }

            // 콤마로 구분된 권한 문자열을 GrantedAuthority 컬렉션으로 변환
            Collection<? extends GrantedAuthority> authorities = Arrays.stream(authClaim.split(","))
                    .filter(role -> role != null && !role.trim().isEmpty())
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            if (authorities.isEmpty()) {
                log.warn("No valid authorities found in token");
                return null;
            }

            // Spring Security User 객체 생성 및 Authentication 토큰 반환
            User principal = new User(claims.getSubject(), "", authorities);
            return new UsernamePasswordAuthenticationToken(principal, "", authorities);
        } catch (Exception e) {
            log.error("Error parsing token: {}", e.getMessage());
            return null;
        }
    }


    /**
     * JWT 토큰의 유효성을 검증
     * @param token 검증할 JWT 토큰
     * @return 토큰 유효성 (true: 유효, false: 무효)
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(key)  // 서명 검증용 키 설정
                    .build()
                    .parseClaimsJws(token);  // 토큰 파싱 및 서명/만료시간 검증
            return true;
        } catch (SecurityException | MalformedJwtException e) {
            log.warn("Invalid JWT Token: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT Token");
        } catch (UnsupportedJwtException e) {
            log.warn("Unsupported JWT Token");
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims string is empty");
        }
        return false;
    }

    /**
     * JWT 토큰에서 Claims 정보를 추출
     * @param accessToken JWT Access Token
     * @return Claims 객체 (토큰이 만료된 경우에도 Claims 반환)
     */
    private Claims parseClaims(String accessToken) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(accessToken)
                    .getBody();  // Claims 부분만 추출
        } catch (ExpiredJwtException e) {
            // 토큰이 만료되어도 Claims 정보는 반환
            return e.getClaims();
        }
    }
}

