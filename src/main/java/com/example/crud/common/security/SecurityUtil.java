package com.example.crud.common.security;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.data.member.service.MemberFindService;
import com.example.crud.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtil {

    private final MemberFindService memberFindService;

    /**
     * 로그인한 사용자의 Member Entity Search
     *
     * @return Member
     * @Throws BaseException
     */
    public Member getCurrentMember() {
        String email = getCurrentUserEmail();
        return memberFindService.getMemberByEmail(email);
    }

    /**
     * 로그인한 사용자의 ID조회
     *
     * @return ID
     */
    public Long getCurrentMemberId() {
        return getCurrentMember().getNumber();
    }

    /**
     * 현재 로그인한 사용자의 이메일 조회
     *
     * @return Email
     * @throws com.example.crud.common.exception.BaseException
     */
    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new BaseException(ErrorCode.INVALID_CREDENTIALS);
        }

        return authentication.getName();
    }

    /**
     * 인증된 사용자인지 확인
     *
     * @return 인증여부
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null &&
                authentication.isAuthenticated() &&
                !(authentication instanceof AnonymousAuthenticationToken);
    }
}
