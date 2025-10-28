package com.example.crud.data.member.service.password;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.common.security.SecurityUtil;
import com.example.crud.data.member.dto.request.ChangePasswordRequest;
import com.example.crud.entity.Member;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 비밀번호 변경 서비스
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ChangePasswordService {

    private final SecurityUtil securityUtil;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        // 현재 사용자 조회
        Member member = securityUtil.getCurrentMember();

        // 비밀번호 일치 검증 (Service에서 처리)
        if (!request.newPassword().equals(request.confirmPassword())) {
            throw new BaseException(ErrorCode.PASSWORD_MISMATCH);
        }

        // 현재 비밀번호 확인
        if (!passwordEncoder.matches(request.currentPassword(), member.getPassword())) {
            throw new BaseException(ErrorCode.INVALID_PASSWORD);
        }

        // 도메인 메서드로 비밀번호 변경
        String encodedPassword = passwordEncoder.encode(request.newPassword());
        member.changePassword(encodedPassword);

        // 변경 감지 + @PreUpdate 자동 실행
    }
}
