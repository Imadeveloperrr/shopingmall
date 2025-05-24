package com.example.crud.repository;

import com.example.crud.entity.Member;
import org.apache.ibatis.annotations.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByEmail(String username);

    boolean existsByEmail(String email);

    boolean existsByNickname(String nickname);

    void deleteByEmail(String email);

    @Query("""
    SELECT c.member FROM Conversation c 
    WHERE c.id = :conversationId
    """)
    Optional<Member> findByConversationId(@Param("conversationId") Long conversationId);

    @Query("""
    SELECT DISTINCT m FROM Member m 
    JOIN m.conversations c 
    JOIN c.messages msg 
    WHERE msg.content LIKE %:keyword% 
    AND m.number != :excludeMemberId
    """)
    List<Member> findMembersWithSimilarInterests(
            @Param("keyword") String keyword,
            @Param("excludeMemberId") Long excludeMemberId
    );
}
