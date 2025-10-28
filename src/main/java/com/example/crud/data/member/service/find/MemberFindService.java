package com.example.crud.data.member.service.find;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import com.example.crud.common.mapper.MemberMapper;
import com.example.crud.data.member.dto.response.MemberResponse;
import com.example.crud.data.member.exception.MemberNotFoundException;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Member 조회 전용 서비스
 * - 단일 책임 원칙에 따라 조회만 담당
 *
 * 기술 스택 혼용:
 * - JPA Repository: 단건 조회, 조건 검색 (타입 안전, 간결)
 * - MyBatis Mapper: 복잡한 조인, 대량 조회 (성능 최적화)
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberFindService {

    private final MemberRepository memberRepository;
    private final MemberMapper memberMapper;

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

    /**
     * 현재 로그인한 회원 조회 (Entity)
     * - 프로필 수정 등 엔티티가 필요한 경우 사용
     */
    public Member getCurrentMemberEntity() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new BaseException(ErrorCode.INVALID_CREDENTIALS);
        }
        return getMemberByEmail(authentication.getName());
    }

    /**
     * 현재 로그인한 회원 조회 (DTO)
     */
    public MemberResponse getCurrentMember() {
        Member member = getCurrentMemberEntity();
        return convertToResponse(member);
    }

    /**
     * 전체 회원 조회 (DTO 목록)
     */
    public List<MemberResponse> getAllMembers() {
        return memberMapper.findAll()
                .stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }

    /**
     * 이메일로 Member 엔티티 조회
     */
    public Member getMemberEntity(String email) {
        return memberRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("사용자를 찾을 수 없습니다: " + email));
    }

    /**
     * Entity → Response DTO 변환
     */
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
