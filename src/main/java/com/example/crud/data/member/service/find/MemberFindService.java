package com.example.crud.data.member.service.find;

import com.example.crud.common.mapper.MemberMapper;
import com.example.crud.common.security.SecurityUtil;
import com.example.crud.data.member.converter.MemberConverter;
import com.example.crud.data.member.dto.response.MemberResponse;
import com.example.crud.data.member.exception.MemberNotFoundException;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Member 조회 전용 서비스
 *
 * 의존성:
 * - MemberRepository (JPA)
 * - MemberMapper (MyBatis)
 * - MemberConverter (MapStruct)
 * - SecurityUtil (인증 정보)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberFindService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;
    private final MemberConverter memberConverter;
    private final SecurityUtil securityUtil;

    public Member getMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new MemberNotFoundException(memberId));
    }

    public Member getMemberByEmail(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new MemberNotFoundException(email));
    }

    /**
     * 현재 로그인한 회원 조회 (Entity)
     */
    public Member getCurrentMemberEntity() {
        return securityUtil.getCurrentMember();
    }

    /**
     * 현재 로그인한 회원 조회 (DTO)
     */
    public MemberResponse getCurrentMember() {
        Member member = getCurrentMemberEntity();
        return memberConverter.toResponse(member);
    }

    /**
     * 전체 회원 조회 (MyBatis + MapStruct)
     */
    public List<MemberResponse> getAllMembers() {
        return memberMapper.findAll()
                .stream()
                .map(memberConverter::toResponse)
                .collect(Collectors.toList());
    }

    public Member getMemberEntity(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));
    }
}
