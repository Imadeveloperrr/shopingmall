package com.example.crud.data.member.service.profile;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.data.member.dto.MemberDto;
import com.example.crud.data.member.dto.MemberResponseDto;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 프로필 수정 서비스
 * - 확장 가능성 없음 (구체 클래스)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UpdateMemberProfileService {

    private final MemberRepository memberRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public MemberResponseDto updateMember(MemberDto memberDto) {
        Member member = getCurrentMember();
        validateMemberUpdate(member, memberDto);

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
}
