package com.example.crud.common.security;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Spring Security 인증 정보 유틸리티
 *
 * 설계:
 * - MemberRepository 직접 사용 (순환 참조 방지)
 * - 인증 정보 조회 책임만 담당
 */
@Component
@RequiredArgsConstructor
public class SecurityUtil {

    private final MemberRepository memberRepository;

    /**
     * 현재 로그인한 사용자의 Member Entity 조회
     *
     * @return Member
     * @throws BaseException 인증 실패 또는 사용자 없음
     */
    public Member getCurrentMember() {
        String email = getCurrentUserEmail();
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new BaseException(ErrorCode.MEMBER_NOT_FOUND));
    }

    /**
     * 현재 로그인한 사용자의 ID 조회
     *
     * @return Member ID
     */
    public Long getCurrentMemberId() {
        return getCurrentMember().getNumber();
    }

    /**
     * 현재 로그인한 사용자의 이메일 조회
     *
     * @return Email
     * @throws BaseException 인증 실패
     */
    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
            || !authentication.isAuthenticated()
            || authentication instanceof AnonymousAuthenticationToken) {
            throw new BaseException(ErrorCode.INVALID_CREDENTIALS);
        }

        return authentication.getName();
    }

    /**
     * 인증된 사용자인지 확인
     *
     * @return 인증 여부
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
            && authentication.isAuthenticated()
            && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
