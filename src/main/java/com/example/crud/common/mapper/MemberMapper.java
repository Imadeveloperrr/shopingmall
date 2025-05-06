package com.example.crud.common.mapper;

import com.example.crud.entity.Member;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface MemberMapper {
    //@Select("SELECT * FROM member")
    List<Member> findAll();

    void updateMember(Member member);
}
