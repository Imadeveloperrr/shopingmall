package com.example.crud.data.member.service;

import com.example.crud.security.JwtToken;

public interface MemberService {
    JwtToken signln(String username, String password);
}
