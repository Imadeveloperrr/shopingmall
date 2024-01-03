package com.example.crud.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "member")
public class Member implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int number;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String name;

    @Column(nullable = true)
    private String address;

    @Column(nullable = true)
    private int phoneNumber;

    @Column(nullable = true)
    @Lob
    private String introduction;

    @Column(nullable = false)
    private String nickname;

    private LocalDateTime createAt;
    private LocalDateTime updateAt;

    @ElementCollection(fetch = FetchType.EAGER) // 즉시 로딩 즉 데이터베이스에서 읽을때 이 컬렉션도 함께 로딩을 의미
    @Builder.Default
    private List<String> roles = new ArrayList<>();
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return this.roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
    }

    @Override
    public String getUsername() {
        return this.email;
    }


    @Override // 계정이 만료되지 않았는지를 반환
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override // 계정이 잠겨있지 않은지를 반환
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override // 계정의 자격증명(비밀번호)이 만료되지 않았는지를 반환
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override // 계정이 활성화(사용 가능) 상태인지를 반환
    public boolean isEnabled() {
        return true;
    }
}
