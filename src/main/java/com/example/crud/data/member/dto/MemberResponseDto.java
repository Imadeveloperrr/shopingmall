package com.example.crud.data.member.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MemberResponseDto {
    private Long number;
    private String password;
    private String name;
    private String email;
    private String nickname;
    private String phoneNumber;
    private String introduction;
    private String address;
}
