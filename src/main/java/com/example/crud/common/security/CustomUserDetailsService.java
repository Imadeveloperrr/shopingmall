package com.example.crud.common.security;

import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor // 하이
public class CustomUserDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    // signIn 메서드의 UsernamePasswordAuthenticationToken에 저장된 사용자 이름이 인자로 전달!
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Member member = memberRepository.findByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("해당하는 회원을 찾을 수 없습니다."));
        return new CustomUserDetails(member);
    }
}
