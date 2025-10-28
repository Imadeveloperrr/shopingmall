package com.example.crud.data.member.service.profile;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.common.security.SecurityUtil;
import com.example.crud.data.member.converter.MemberConverter;
import com.example.crud.data.member.dto.request.UpdateProfileRequest;
import com.example.crud.data.member.dto.response.MemberResponse;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 프로필 수정 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UpdateMemberProfileService {

    private final MemberRepository memberRepository;
    private final SecurityUtil securityUtil;
    private final MemberConverter memberConverter;

    @Transactional
    public MemberResponse updateMember(UpdateProfileRequest request) {
        // SecurityUtil 사용 (순환 참조 없음)
        Member member = securityUtil.getCurrentMember();

        // 닉네임 중복 검증
        validateNicknameUniqueness(member, request);

        // 도메인 메서드로 업데이트
        member.updateProfile(
            request.name(),
            request.nickname(),
            request.phoneNumber(),
            request.address(),
            request.introduction()
        );

        // 변경 감지로 자동 저장 + @PreUpdate 자동 실행

        // Converter로 통일
        return memberConverter.toResponse(member);
    }

    private void validateNicknameUniqueness(Member member, UpdateProfileRequest request) {
        if (request.nickname() != null
            && !member.getNickname().equals(request.nickname())
            && memberRepository.existsByNickname(request.nickname())) {
            throw new BaseException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }
}
