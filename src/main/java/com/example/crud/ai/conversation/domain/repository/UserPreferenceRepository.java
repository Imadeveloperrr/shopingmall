package com.example.crud.ai.conversation.domain.repository;

import com.example.crud.ai.conversation.domain.entity.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserPreferenceRepository extends JpaRepository<UserPreference, Long> {
    Optional<UserPreference> findByMember_Number(Long memberId);
}
