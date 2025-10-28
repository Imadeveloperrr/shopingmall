package com.example.crud.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 회원 엔티티
 *
 * 설계 원칙:
 * - @Setter 제거: 도메인 메서드로만 상태 변경
 * - 타임스탬프 자동 관리: @PrePersist/@PreUpdate만 사용
 * - Builder 패턴: 생성 시에만 사용
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long number;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = true)
    private String address;

    @Column(nullable = true)
    private String phoneNumber;

    @Column(nullable = true)
    private String mobileNumber;

    @Column(nullable = true)
    @Lob
    private String introduction;

    @Column(name = "create_at")
    private LocalDateTime createdAt;

    @Column(name = "update_at")
    private LocalDateTime updatedAt;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "member_roles", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "role")
    @Builder.Default
    private List<String> roles = new ArrayList<>();

    /**
     * 프로필 정보 업데이트 (부분 갱신)
     *
     * @param name 이름 (null이면 변경 안 함)
     * @param nickname 닉네임 (null이면 변경 안 함)
     * @param phoneNumber 전화번호 (null이면 변경 안 함)
     * @param address 주소 (null이면 변경 안 함)
     * @param introduction 소개 (null이면 변경 안 함)
     */
    public void updateProfile(String name, String nickname, String phoneNumber,
                              String address, String introduction) {
        if (name != null) {
            this.name = name;
        }
        if (nickname != null) {
            this.nickname = nickname;
        }
        if (phoneNumber != null) {
            this.phoneNumber = phoneNumber;
        }
        if (address != null) {
            this.address = address;
        }
        if (introduction != null) {
            this.introduction = introduction;
        }
    }

    /**
     * 비밀번호 변경
     *
     * @param encodedPassword 암호화된 비밀번호
     */
    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 생성 시각 자동 설정
     */
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    /**
     * 수정 시각 자동 갱신
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
