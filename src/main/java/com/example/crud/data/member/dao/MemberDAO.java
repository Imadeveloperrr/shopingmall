package com.example.crud.data.member.dao;

import com.example.crud.entity.Member;

import java.util.Optional;

public interface MemberDAO {
    Member saveMember(Member member);

    Optional<Member> getMember(String email);

    void deleteMember(String email);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

}
