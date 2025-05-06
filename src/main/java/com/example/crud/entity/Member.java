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
