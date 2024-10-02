package com.example.crud.security;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.security.servlet.PathRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.ExceptionHandlingConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtTokenProvider jwtTokenProvider;
    @Bean // 빨간줄들은 Spring Security 7 에서 deprecated 될 메서드들을 warning 한것임
    public SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity
                // Rest API 이므로 basic auth 및 csrf 보안을 사용하지 않음.
                .httpBasic(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                // JWT를 사용하기 때문에 세션을 사용하지 않음.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // Exception Handling 설정
                .exceptionHandling(this::configureExceptionHandling)
                .and()

                .logout()
                    .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                    .logoutSuccessUrl("/")
                    .invalidateHttpSession(true)
                    .deleteCookies("accessToken", "refreshToken")
                .and()
                // 권한 설정
                .authorizeHttpRequests()
                    // 해당 API에 대해서는 모든 요청을 허가.
                    .requestMatchers("/").permitAll() //
                    .requestMatchers("/register").permitAll() //
                    .requestMatchers("/login").permitAll() //
                    .requestMatchers("/logout").permitAll()

                    // USER 권한이 있어야 요청할 수 있음.
                    .requestMatchers("/mypage/**").hasRole("USER")
                    .requestMatchers("/product/**").hasRole("USER")
                    //  정적 파일에 대한 권한 허용을 설정
                    .requestMatchers(PathRequest.toStaticResources().atCommonLocations()).permitAll()
                    // 이 밖에 모든 요청에 대해서 인증을 필요로 한다는 설정.
                    .anyRequest().authenticated()
                .and()

                // JWT 인증을 위하여 직접 구현한 필터를 UsernamePasswordAuthenticationFilter 전에 실행.
                .addFilterBefore(new JwtAuthenticationFilter(jwtTokenProvider), UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    private void configureExceptionHandling(ExceptionHandlingConfigurer<HttpSecurity> exceptions) throws Exception {
        exceptions
                .authenticationEntryPoint(new CustomAuthenticationEntryPoint())
                .accessDeniedHandler(new CustomAccessDeniedHandler());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
