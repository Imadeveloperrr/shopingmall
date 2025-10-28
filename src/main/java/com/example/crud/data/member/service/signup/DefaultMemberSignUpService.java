package com.example.crud.data.member.service.signup;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.data.member.dto.request.SignUpRequest;
import com.example.crud.data.member.dto.response.MemberResponse;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 기본 회원 가입 서비스 구현체
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultMemberSignUpService implements MemberSignUpService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    @Override
    public MemberResponse signUp(SignUpRequest request) {
        if (memberRepository.existsByEmail(request.getEmail())) {
            throw new BaseException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (memberRepository.existsByNickname(request.getNickname())) {
            throw new BaseException(ErrorCode.DUPLICATE_NICKNAME);
        }

        List<String> roles = new ArrayList<>();
        roles.add("ROLE_USER");

        Member member = Member.builder()
                .email(request.getEmail())
                .name(request.getName())
                .password(passwordEncoder.encode(request.getPassword()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .nickname(request.getNickname())
                .roles(roles)
                .build();

        Member savedMember = memberRepository.save(member);

        return MemberResponse.builder()
                .number(savedMember.getNumber())
                .email(savedMember.getEmail())
                .name(savedMember.getName())
                .nickname(savedMember.getNickname())
                .build();
    }
}
