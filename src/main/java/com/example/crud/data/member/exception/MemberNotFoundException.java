package com.example.crud.data.member.exception;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;

/**
 * 회원을 찾을 수 없을 때 발생하는 예외
 */
public class MemberNotFoundException extends BaseException {

    public MemberNotFoundException(Long memberId) {
        super(ErrorCode.MEMBER_NOT_FOUND, memberId);
    }

    public MemberNotFoundException(String email) {
        super(ErrorCode.MEMBER_NOT_FOUND, email);
    }
}
