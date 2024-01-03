package com.example.crud.data.member.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MemberResponseDto {
    private int number;
    private String password;
    private String name;
    private String email;
    private String nickname;
    private int phoneNumber;
    private String introduction;
    private String address;
}
