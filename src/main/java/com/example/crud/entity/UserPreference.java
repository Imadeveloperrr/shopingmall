package com.example.crud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_preference")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserPreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 회원과 1:1 매핑
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    // 구조화된 선호 정보를 JSON 문자열로 저장 (예: {"category": "CASUAL", "keywords": ["편안함", "심플"]})
    @Column(columnDefinition = "TEXT")
    private String preferences;

    @Column
    private LocalDateTime lastUpdated;
}
