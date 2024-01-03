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
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final MemberDAO memberDao;

    @Transactional // 새로운 @Transactional 어노테이션을 사용하여 기본 설정을 오버라이딩함. 즉, 이 메서드에서는 데이터를 변경할 수 있음.
    @Override
    public JwtToken signIn(String username, String password) {
        // 1. username + password 를 기반으로 Authentication 객체 생성
        // 이때 authentication은 인증 여부를 확인하는 authenticated 값이 false
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(username, password);

        /* 2. 실제 검증.
        AuthenticationManager가 authenticate() 메서드를 통해 요청된 User 대한 검증 진행
        - AuthenticationProvider 내부에서 UserDetailesService의 loadUserByUsername 메서드가 호출됨.
        - 이 과정에서 authenticationToken 인스턴스에 저장된 사용자 이름이 loadUserByUsername 인자로 전달!
        - 그 후 Override한 loadUserByUsername은 UserDetails 객체를 반환 AuthenticationProvider는 반환된 UserDetails 객체와
        authenticationToken 인스턴스 즉 UsernamePasswordAuthenticationToken 의 자격증명을 비교하여 인증 과정을 완료.
        비밀번호가 일치하면 사용자는 인증되고 authentication은 authenticated 값이 true로 업데이트됩니다잉
        */
        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

        // 3. 인증 정보를 기반으로 JWT 토큰 생성
        JwtToken jwtToken = jwtTokenProvider.generateToken(authentication);

        /*
        * UsernamePasswordAuthenticationToken 객체의 username, password를 입력받아
        * Authentication 객체를 생성한후 그 객체를 다시 AuthenticationManager의 authenticate 메서드를
        * 사용해서 Authentication 객체를 입력시킨뒤 인증된 사용자의 정보를 담고있는 Authentication 객체를
        * 반환하고 !!!! 나서 JwtTokenProvider의 generateToken 메서드로 토큰을 만들어버린다...
        * */
        return jwtToken;
    }

    @Transactional
    @Override
    public MemberResponseDto signUp(MemberDto memberDto) {
        if (memberDao.existsByEmail(memberDto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일 입니다.");
        } else if (memberDao.existsByNickname(memberDto.getNickname())) {
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

    @Override
    public MemberResponseDto getMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Member member = memberDao.getMember(authentication.getName())
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
    public MemberResponseDto updateMemBer(MemberDto memberDto) {
        if (memberDao.existsByEmail(memberDto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일 입니다.");
        } else if (memberDao.existsByNickname(memberDto.getNickname())) {
            throw new IllegalArgumentException("이미 사용 중인 닉네임 입니다.");
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Member member = memberDao.getMember(authentication.getName())
                .orElseThrow(() -> new NoSuchElementException("ERROR : 존재하지 않는 이메일"));

        member.setUpdateAt(LocalDateTime.now());
        member.setName(memberDto.getName());
        member.setEmail(memberDto.getEmail());
        member.setNickname(memberDto.getNickname());
        member.setPassword(memberDto.getPassword());
        member.setAddress(memberDto.getAddress());
        member.setIntroduction(memberDto.getIntroduction());
        member.setPhoneNumber(memberDto.getPhoneNumber());

        Member saveMember = memberDao.saveMember(member);

        return MemberResponseDto.builder()
                .number(saveMember.getNumber())
                .email(saveMember.getEmail())
                .name(saveMember.getName())
                .password(saveMember.getPassword())
                .nickname(saveMember.getNickname())
                .address(saveMember.getAddress())
                .phoneNumber(saveMember.getPhoneNumber())
                .introduction(saveMember.getIntroduction())
                .build();
    }
}

