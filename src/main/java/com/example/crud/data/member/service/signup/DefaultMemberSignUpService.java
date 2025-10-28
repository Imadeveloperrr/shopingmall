package com.example.crud.data.member.service.signup;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.data.member.converter.MemberConverter;
import com.example.crud.data.member.dto.request.SignUpRequest;
import com.example.crud.data.member.dto.response.MemberResponse;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 기본 회원 가입 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DefaultMemberSignUpService implements MemberSignUpService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;
    private final MemberConverter memberConverter;

    @Transactional
    @Override
    public MemberResponse signUp(SignUpRequest request) {
        // 중복 검증
        if (memberRepository.existsByEmail(request.email())) {
            throw new BaseException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (memberRepository.existsByNickname(request.nickname())) {
            throw new BaseException(ErrorCode.DUPLICATE_NICKNAME);
        }

        // 가변 리스트 생성
        List<String> roles = new ArrayList<>();
        roles.add("ROLE_USER");

        // Entity 생성 (타임스탬프는 @PrePersist에서 자동 설정)
        Member member = Member.builder()
                .email(request.email())
                .name(request.name())
                .nickname(request.nickname())
                .password(passwordEncoder.encode(request.password()))
                .roles(roles)
                .build();

        Member savedMember = memberRepository.save(member);

        // Converter로 통일
        return memberConverter.toResponse(savedMember);
    }
}
