package com.example.crud.utility;

import com.example.crud.entity.Member;
import com.example.crud.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            // 비밀번호가 평문인지 아닌지 확인.
            if (plainPassword != null && !plainPassword.startsWith("{bcrypt}") && !plainPassword.startsWith("$2a$")) {
                String encodedPassword = passwordEncoder.encode(plainPassword);
                member.setPassword(encodedPassword);

                memberMapper.updateMember(member);
                System.out.println("비밀번호 재 인코딩: " + member.getEmail());
            }
        }

    }
}
