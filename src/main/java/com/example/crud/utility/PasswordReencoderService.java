package com.example.crud.utility;

import com.example.crud.entity.Member;
import com.example.crud.mapper.MemberMapper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PasswordReencoderService {

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void reencodeAllPassword() {
        List<Member> members = memberMapper.findAll();
        for (Member member : members) {
            String plainPassword = member.getPassword();
            if (plainPassword != null && plainPassword.startsWith("{bcrypt}") && !plainPassword.startsWith("$2a$")) {
                String encodedPassword = passwordEncoder.encode(plainPassword);
                member.setPassword(encodedPassword);

                System.out.println("비밀번호 재 인코딩: " + member.getEmail());
            }
        }

    }
}
