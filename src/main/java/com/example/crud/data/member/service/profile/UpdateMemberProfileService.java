package com.example.crud.data.member.service.profile;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.data.member.dto.request.UpdateProfileRequest;
import com.example.crud.data.member.dto.response.MemberResponse;
import com.example.crud.data.member.service.find.MemberFindService;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
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
    private final MemberFindService memberFindService;

    @Transactional
    public MemberResponse updateMember(UpdateProfileRequest request) {
        Member member = memberFindService.getCurrentMemberEntity();

        validateNicknameUniqueness(member, request);

        member.updateProfile(
            request.getName(),
            request.getNickname(),
            request.getPhoneNumber(),
            request.getAddress(),
            request.getIntroduction()
        );

        Member savedMember = memberRepository.save(member);

        return convertToResponse(savedMember);
    }

    private void validateNicknameUniqueness(Member member, UpdateProfileRequest request) {
        if (request.getNickname() != null
            && !member.getNickname().equals(request.getNickname())
            && memberRepository.existsByNickname(request.getNickname())) {
            throw new BaseException(ErrorCode.DUPLICATE_NICKNAME);
        }
    }

    private MemberResponse convertToResponse(Member member) {
        return MemberResponse.builder()
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
