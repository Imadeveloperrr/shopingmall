package com.example.crud.data.member.service.auth;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.common.security.JwtToken;
import com.example.crud.common.security.JwtTokenProvider;
import com.example.crud.data.token.TokenRequestDto;
import com.example.crud.entity.RefreshToken;
import com.example.crud.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JWT 기반 회원 인증 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class JwtMemberAuthService implements MemberAuthService {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    @Override
    public JwtToken signIn(String username, String password, boolean rememberMe) {
        try {
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(username, password);

            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
            JwtToken jwtToken = jwtTokenProvider.generateToken(authentication, rememberMe);

            RefreshToken refreshToken = RefreshToken.builder()
                    .key(authentication.getName())
                    .token(jwtToken.getRefreshToken())
                    .rememberMe(rememberMe)
                    .build();
            refreshTokenRepository.save(refreshToken);

            return jwtToken;
        } catch (AuthenticationException e) {
            log.error("Authentication failed for user {}: {}", username, e.getMessage());
            throw new BaseException(ErrorCode.INVALID_CREDENTIALS);
        } catch (Exception e) {
            log.error("Login failed for user {}: {}", username, e.getMessage());
            throw new BaseException(ErrorCode.LOGIN_FAILED);
        }
    }

    @Override
    public JwtToken reissue(TokenRequestDto tokenRequestDto) {
        if (!jwtTokenProvider.validateToken(tokenRequestDto.getRefreshToken())) {
            throw new BaseException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        try {
            Authentication authentication = jwtTokenProvider.getAuthentication(tokenRequestDto.getAccessToken());

            RefreshToken refreshToken = refreshTokenRepository.findById(authentication.getName())
                    .orElseThrow(() -> new BaseException(ErrorCode.INVALID_REFRESH_TOKEN));

            if (!refreshToken.getToken().equals(tokenRequestDto.getRefreshToken())) {
                throw new BaseException(ErrorCode.MISMATCH_REFRESH_TOKEN);
            }

            boolean rememberMe = refreshToken.isRememberMe();
            JwtToken newJwtToken = jwtTokenProvider.generateToken(authentication, rememberMe);

            refreshToken.updateToken(newJwtToken.getRefreshToken());
            refreshTokenRepository.save(refreshToken);

            return newJwtToken;
        } catch (BaseException e) {
            throw e;
        } catch (Exception e) {
            log.error("Token reissue failed: {}", e.getMessage());
            throw new BaseException(ErrorCode.TOKEN_EXPIRED);
        }
    }
}
