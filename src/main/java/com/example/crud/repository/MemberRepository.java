package com.example.crud.repository;

import com.example.crud.entity.Member;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String username);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    void deleteByEmail(String email);
}
