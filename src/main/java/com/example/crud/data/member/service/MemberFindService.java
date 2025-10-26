package com.example.crud.data.member.service;

import com.example.crud.data.member.exception.MemberNotFoundException;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Member 조회 전용 서비스
 * - 단일 책임 원칙에 따라 조회만 담당
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberFindService {

    private final MemberRepository memberRepository;

    /**
     * 회원 조회 (ID로)
     */
    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }

    /**
     * 회원 조회 (이메일로)
     */
    public Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberNotFoundException(email));
    }
}
