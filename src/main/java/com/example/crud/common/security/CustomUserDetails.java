package com.example.crud.common.security;

import com.example.crud.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security UserDetails 구현 클래스
 * Member 엔티티를 기반으로 Spring Security에서 사용할 사용자 정보를 제공
 */
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final Member member;

    /**
     * 사용자의 권한(역할) 정보를 반환
     * @return Spring Security에서 사용할 권한 목록
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return member.getRoles().stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    /** 암호화된 비밀번호 반환 */
    @Override
    public String getPassword() {
        return member.getPassword();
    }

    /** 사용자명(이메일) 반환 */
    @Override
    public String getUsername() {
        return member.getEmail();
    }

    /** 계정 만료 여부 (기본: 비만료) */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /** 계정 잠김 여부 (기본: 비잠김) */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /** 인증 정보 만료 여부 (기본: 비만료) */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /** 계정 활성화 상태 (기본: 활성화) */
    @Override
    public boolean isEnabled() {
        return true;
    }
}
