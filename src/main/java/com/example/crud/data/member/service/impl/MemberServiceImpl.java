package com.example.crud.data.member.service.impl;

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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MemberServiceImpl implements MemberService {

    private final MemberRepository memberRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Transactional // 새로운 @Transactional 어노테이션을 사용하여 기본 설정을 오버라이딩함. 즉, 이 메서드에서는 데이터를 변경할 수 있음.
    @Override
    public JwtToken signIn(String username, String password) {
        // 1. 사용자 이름과 비밀번호를 기반으로 UsernamePasswordAuthenticationToken 객체 생성
        // 이 단계에서의 authenticationToken은 아직 인증되지 않았으므로, authenticated 값이 false입니다.
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

    /* 2. 실제 인증 과정을 처리합니다.
       AuthenticationManager의 authenticate 메서드를 호출하여 인증을 시도합니다.
       - 이 과정에서 설정된 AuthenticationProvider가 사용됩니다.
       - AuthenticationProvider 내부에서는 UserDetailsService의 loadUserByUsername 메서드가 호출되어 사용자 정보를 불러옵니다.
       - 불러온 UserDetails 객체와 UsernamePasswordAuthenticationToken 내의 자격 증명(비밀번호)을 비교합니다.
       - 비밀번호가 일치하면, authentication 객체의 authenticated 값이 true로 설정되어 인증 과정을 성공적으로 마칩니다.
    */
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 3. 인증된 Authentication 객체를 기반으로 JWT 토큰을 생성합니다.
        JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);

    /* 요약:
       - UsernamePasswordAuthenticationToken 객체에 사용자 이름과 비밀번호를 입력하여 Authentication 객체를 생성합니다.
       - 이 객체를 AuthenticationManager의 authenticate 메서드에 전달하여 인증을 시도합니다.
       - 인증이 성공하면, 인증된 사용자 정보를 담고 있는 Authentication 객체가 반환됩니다.
       - JwtTokenProvider의 generateToken 메서드를 사용하여 이 인증 정보를 기반으로 JWT 토큰을 생성합니다.
    */
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
        rolse.add("ROLE_USER");
        Member member = Member.builder()
                .email(memberDto.getEmail())
                .name(memberDto.getName())
                .password(passwordEncoder.encode(memberDto.getPassword())) // 비밀번호 인코딩 기존 코딩 : memberDto.getPassword()
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .nickname(memberDto.getNickname())
                .roles(rolse)
                .build();

        Member saveMember = memberRepository.save(member);

        MemberResponseDto memberResponseDto = MemberResponseDto.builder()
                .number(saveMember.getNumber())
                .email(saveMember.getEmail())
                .name(saveMember.getName()) // 응답 DTO password 삭제했음.
                .nickname(saveMember.getNickname())
                .build();
        return memberResponseDto;
    }

    @Override
    public MemberResponseDto getMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Member member = memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NoSuchElementException("Error : 존재하지 않는 이메일 주소"));

        return MemberResponseDto.builder()
                .number(member.getNumber())
                .email(member.getEmail())
                .name(member.getName())
                .password(member.getPassword())
                .nickname(member.getNickname())
                .address(member.getAddress())
                .phoneNumber(member.getPhoneNumber())
                .introduction(member.getIntroduction())
                .build();
    }

    @Override
    @Transactional
    public MemberResponseDto updateMemBer(MemberDto memberDto) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Member member = memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new NoSuchElementException("ERROR : 존재하지 않는 이메일"));

        if (!member.getEmail().equals(memberDto.getEmail()) && memberRepository.existsByEmail(memberDto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일 입니다.");
        }

        if (!member.getNickname().equals(memberDto.getNickname()) && memberRepository.existsByNickname(memberDto.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임 입니다.");
        }

        member.setUpdatedAt(LocalDateTime.now());
        member.setName(memberDto.getName());
        member.setEmail(memberDto.getEmail());
        member.setNickname(memberDto.getNickname());
        member.setPassword(passwordEncoder.encode(memberDto.getPassword())); // 비밀번호 인코딩
        member.setAddress(memberDto.getAddress());
        member.setIntroduction(memberDto.getIntroduction());
        member.setPhoneNumber(memberDto.getPhoneNumber());

        Member saveMember = memberRepository.save(member);

        return MemberResponseDto.builder()
                .number(saveMember.getNumber())
                .email(saveMember.getEmail())
                .name(saveMember.getName()) // 패스워드 제거
                .nickname(saveMember.getNickname())
                .address(saveMember.getAddress())
                .phoneNumber(saveMember.getPhoneNumber())
                .introduction(saveMember.getIntroduction())
                .build();
    }
}

