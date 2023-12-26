package com.example.crud.data.member.dao.impl;

import com.example.crud.data.member.dao.MemberDAO;
import com.example.crud.entity.Member;
import com.example.crud.repository.MemberRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Builder
@RequiredArgsConstructor
@Component
public class MemberDAOImpl implements MemberDAO {

    private final MemberRepository memberRepository;
    @Override
    public Member saveMember(Member member) {
        return memberRepository.save(member);
    }

    @Override
    public Optional<Member> getMember(String memberName) {
        return memberRepository.findByEmail(memberName);
    }
}
