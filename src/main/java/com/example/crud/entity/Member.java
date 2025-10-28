package com.example.crud.entity;

import com.example.crud.data.member.dto.MemberDto;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(nullable = true)
    private String address;

    @Column(nullable = true)
    private String phoneNumber; // 전화번호

    @Column(nullable = true)
    private String mobileNumber; // 휴대폰 번호

    @Column(nullable = true)
    @Lob
    private String introduction;

    @Column(nullable = false)
    private String nickname;

    @Column(name = "create_at")
    private LocalDateTime createdAt;
    @Column(name = "update_at")
    private LocalDateTime updatedAt;

    @ElementCollection(fetch = FetchType.EAGER) // 즉시 로딩 즉 데이터베이스에서 읽을때 이 컬렉션도 함께 로딩을 의미
    @CollectionTable(name = "member_roles", joinColumns = @JoinColumn(name = "member_id"))
    @Column(name = "role")
    @Builder.Default
    private List<String> roles = new ArrayList<>();

    /**
     * 프로필 수정 (부분 갱신 지원)
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
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * 비밀번호 변경
     *
     * @param newPassword 새 비밀번호 (평문)
     * @param passwordEncoder 암호화 도구
     */
    public void changePassword(String newPassword, PasswordEncoder passwordEncoder) {
        this.password = passwordEncoder.encode(newPassword);
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * @deprecated DTO 의존성 제거를 위해 updateProfile 사용 권장
     */
    @Deprecated
    public void update(MemberDto memberDto, PasswordEncoder passwordEncoder) {
        this.updatedAt = LocalDateTime.now();
        this.name = memberDto.getName();
        this.email = memberDto.getEmail();
        this.nickname = memberDto.getNickname();
        this.password = passwordEncoder.encode(memberDto.getPassword());
        this.address = memberDto.getAddress();
        this.introduction = memberDto.getIntroduction();
        this.phoneNumber = memberDto.getPhoneNumber();
    }
}
