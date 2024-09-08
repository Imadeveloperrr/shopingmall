package com.example.crud.data.member.dto;

import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@ToString
public class MemberDto {
    private String password;
    private String name;
    private String email;
    private String nickname;
    private String address;
    private String phoneNumber;
    private String introduction;
    private List<String> roles = new ArrayList<>();
}
