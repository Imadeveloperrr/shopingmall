package com.example.crud.common.security;

import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


/**
 * Spring Security UserDetailsService 구현체
 * 사용자 인증 시 데이터베이스에서 사용자 정보를 조회하여 UserDetails 객체를 반환
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    /**
     * 사용자명(이메일)으로 사용자 정보를 조회하여 UserDetails 객체로 반환
     * Spring Security에서 인증 시 자동으로 호출됨
     *
     * @param username 사용자 이메일 (로그인 ID로 사용)
     * @return Spring Security에서 사용할 UserDetails 객체
     * @throws UsernameNotFoundException 사용자를 찾을 수 없는 경우
     */
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member member = memberRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException(
                    "해당 이메일의 회원을 찾을 수 없습니다: " + username));

        return new CustomUserDetails(member);
    }
}
