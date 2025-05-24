package com.example.crud.data.member.service.impl;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.data.member.service.MemberService;
import com.example.crud.data.token.TokenRequestDto;
import com.example.crud.entity.Member;
import com.example.crud.entity.RefreshToken;
import com.example.crud.common.mapper.MemberMapper;
import com.example.crud.repository.MemberRepository;
import com.example.crud.repository.RefreshTokenRepository;
import com.example.crud.common.security.JwtToken;
import com.example.crud.common.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class MemberServiceImpl implements MemberService {
    private final MemberRepository memberRepository;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final MemberMapper memberMapper;

    @Transactional
    @Override
    public JwtToken signIn(String username, String password, boolean rememberMe) {
        try {
            // 1. 사용자 이름과 비밀번호를 기반으로 UsernamePasswordAuthenticationToken 객체 생성
            // 이 단계에서의 authenticationToken은 아직 인증되지 않았으므로, authenticated 값이 false입니다.
            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(username, password);

            /* 2. 실제 인증 과정을 처리합니다.
               AuthenticationManager의 authenticate 메서드를 호출하여 인증을 시도합니다.
               - 이 과정에서 설정된 AuthenticationProvider가 사용됩니다.
               - AuthenticationProvider 내부에서는 UserDetailsService의 loadUserByUsername 메서드가 호출되어 사용자 정보를 불러옵니다.
               - 불러온 UserDetails 객체와 UsernamePasswordAuthenticationToken 내의 자격 증명(비밀번호)을 비교합니다.
               - 비밀번호가 일치하면, authentication 객체의 authenticated 값이 true로 설정되어 인증 과정을 성공적으로 마칩니다.
            */
            Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);

            // 3. 인증된 Authentication 객체를 기반으로 JWT 토큰을 생성합니다.
            JwtToken jwtToken = jwtTokenProvider.generateToken(authentication, rememberMe);

            /* 요약:
               - UsernamePasswordAuthenticationToken 객체에 사용자 이름과 비밀번호를 입력하여 Authentication 객체를 생성합니다.
               - 이 객체를 AuthenticationManager의 authenticate 메서드에 전달하여 인증을 시도합니다.
               - 인증이 성공하면, 인증된 사용자 정보를 담고 있는 Authentication 객체가 반환됩니다.
               - JwtTokenProvider의 generateToken 메서드를 사용하여 이 인증 정보를 기반으로 JWT 토큰을 생성합니다.
            */

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

        // 1. Refresh Token 검증
        if (!jwtTokenProvider.validateToken(tokenRequestDto.getRefreshToken())) {
            throw new BaseException(ErrorCode.INVALID_REFRESH_TOKEN);  // CustomException -> BaseException
        }

        try {
            // 2. Access Token에서 Authentication 객체 가져오기
            Authentication authentication = jwtTokenProvider.getAuthentication(tokenRequestDto.getAccessToken());

            // 3. 저장소에서 Refresh Token 가져오기
            RefreshToken refreshToken = refreshTokenRepository.findById(authentication.getName())
                    .orElseThrow(() -> new BaseException(ErrorCode.INVALID_REFRESH_TOKEN));  // CustomException -> BaseException

            // 4. Refresh Token 일치 여부 확인
            if (!refreshToken.getToken().equals(tokenRequestDto.getRefreshToken())) {
                throw new BaseException(ErrorCode.MISMATCH_REFRESH_TOKEN);  // CustomException -> BaseException
            }

            // 5. 새로운 토큰 생성
            boolean rememberMe = refreshToken.isRememberMe();
            JwtToken newJwtToken = jwtTokenProvider.generateToken(authentication, rememberMe);

            // 6. Refresh Token Update
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

    @Transactional
    @Override
    public MemberResponseDto signUp(MemberDto memberDto) {
        if (memberRepository.existsByEmail(memberDto.getEmail())) {
            throw new BaseException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (memberRepository.existsByNickname(memberDto.getNickname())) {
            throw new BaseException(ErrorCode.DUPLICATE_NICKNAME);
        }

        List<String> roles = new ArrayList<>();
        roles.add("ROLE_USER");

        Member member = Member.builder()
                .email(memberDto.getEmail())
                .name(memberDto.getName())
                .password(passwordEncoder.encode(memberDto.getPassword()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .nickname(memberDto.getNickname())
                .roles(roles)
                .build();

        Member saveMember = memberRepository.save(member);

        return MemberResponseDto.builder()
                .number(saveMember.getNumber())
                .email(saveMember.getEmail())
                .name(saveMember.getName())
                .nickname(saveMember.getNickname())
                .build();
    }

    @Override
    public MemberResponseDto getMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Member member = memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BaseException(ErrorCode.MEMBER_NOT_FOUND));

        return MemberResponseDto.builder()
                .number(member.getNumber())
                .email(member.getEmail())
                .name(member.getName())
                .nickname(member.getNickname())
                .address(member.getAddress())
                .phoneNumber(member.getPhoneNumber())
                .introduction(member.getIntroduction())
                .build();
    }


    @Transactional
    @Override
    public MemberResponseDto updateMember(MemberDto memberDto) {
        Member member = getCurrentMember();
        validateMemberUpdate(member, memberDto);

        // 엔티티의 update 메서드 사용
        member.update(memberDto, passwordEncoder);
        Member saveMember = memberRepository.save(member);

        return MemberResponseDto.builder()
                .number(saveMember.getNumber())
                .email(saveMember.getEmail())
                .name(saveMember.getName())
                .nickname(saveMember.getNickname())
                .address(saveMember.getAddress())
                .phoneNumber(saveMember.getPhoneNumber())
                .introduction(saveMember.getIntroduction())
                .build();
    }

    private Member getCurrentMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return memberRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new BaseException(ErrorCode.MEMBER_NOT_FOUND));
    }

    private void validateMemberUpdate(Member member, MemberDto memberDto) {
        if (!member.getEmail().equals(memberDto.getEmail()) && memberRepository.existsByEmail(memberDto.getEmail())) {
            throw new BaseException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (!member.getNickname().equals(memberDto.getNickname()) && memberRepository.existsByNickname(memberDto.getNickname())) {
            throw new BaseException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    public List<MemberResponseDto> getAllMembers() {
        List<Member> members = memberMapper.findAll();
        return members.stream()
                .map(this::convertToMemberResponseDto)
                .collect(Collectors.toList());
    }

    public Member getMemberEntity(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));
    }

    private MemberResponseDto convertToMemberResponseDto(Member member) {
        return MemberResponseDto.builder()
                .number(member.getNumber())
                .email(member.getEmail())
                .name(member.getName())
                .nickname(member.getNickname())
                .address(member.getAddress())
                .phoneNumber(member.getPhoneNumber())
                .introduction(member.getIntroduction())
                .build();
    }
}

