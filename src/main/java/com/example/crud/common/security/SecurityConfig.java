package com.example.crud.common.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

/**
 * Spring Security 설정 클래스
 * JWT 토큰 기반 인증과 권한 관리를 담당
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 보안 필터 체인 설정
     * JWT 토큰 기반 인증 및 권한 체크 구성
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                // REST API 설정: Basic Auth 사용, CSRF 비활성화
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())

                // JWT 토큰 기반 인증: 세션 사용하지 않음
                .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 인증/인가 예외 처리 설정
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                        .accessDeniedHandler(new CustomAccessDeniedHandler())
                )

                // 로그아웃 설정
                .logout(logout -> logout
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl("/")
                        .invalidateHttpSession(true)
                        .deleteCookies("accessToken", "refreshToken")
                )

                // URL별 권한 설정
                .authorizeHttpRequests(authorize -> authorize
                        // 정적 리소스: 모든 사용자 허용
                        .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/img/**", "/favicon.ico").permitAll()

                        // 공개 페이지: 인증 불필요
                        .requestMatchers("/", "/register", "/login", "/logout").permitAll()

                        // 보호된 페이지: USER 권한 필요
                        .requestMatchers("/mypage/**").hasRole("USER")
                        .requestMatchers("/cart/**").hasRole("USER")
                        .requestMatchers("/product/**").hasRole("USER")

                        // 나머지: 인증 필요
                        .anyRequest().authenticated()
                )

                // JWT 인증 필터 추가: UsernamePasswordAuthenticationFilter 이전에 실행
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider),
                    UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * 비밀번호 암호화 인코더
     * BCrypt 알고리즘 사용
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
