package com.example.crud.data.member.service.impl;


import com.example.crud.data.member.dao.MemberDAO;
import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import com.example.crud.security.JwtToken;
import com.example.crud.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final MemberDAO memberDao;

    @Transactional // 새로운 @Transactional 어노테이션을 사용하여 기본 설정을 오버라이딩함. 즉, 이 메서드에서는 데이터를 변경할 수 있음.
    @Override
    public JwtToken signIn(String username, String password) {
        // 1. username + password 를 기반으로 Authentication 객체 생성
        // 이때 authentication은 인증 여부를 확인하는 authenticated 값이 false
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

        // 2. 실제 검증. authenticate() 메서드를 통해 요청된 User 대한 검증 진행
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 3. 인증 정보를 기반으로 JWT 토큰 생성
        JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);
        return jwtToken;
    }

    @Transactional
    @Override
    public MemberResponseDto signUp(MemberDto memberDto) {
        if (memberRepository.existsByEmail(memberDto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일 입니다.");
        } else if (memberRepository.existsByNickname(memberDto.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임 입니다.");
        }

        List<String> rolse = new ArrayList<>();
        rolse.add("USER");
        Member member = Member.builder()
                .email(memberDto.getEmail())
                .name(memberDto.getName())
                .password(memberDto.getPassword())
                .createAt(LocalDateTime.now())
                .updateAt(LocalDateTime.now())
                .nickname(memberDto.getNickname())
                .roles(rolse)
                .build();

        Member saveMember = memberDao.saveMember(member);

        MemberResponseDto memberResponseDto = MemberResponseDto.builder()
                .number(saveMember.getNumber())
                .email(saveMember.getEmail())
                .name(saveMember.getName())
                .password(saveMember.getPassword())
                .nickname(saveMember.getNickname())
                .build();
        return memberResponseDto;
    }
}

