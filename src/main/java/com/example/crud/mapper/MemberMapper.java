package com.example.crud.mapper;

import com.example.crud.entity.Member;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MemberMapper {
    //@Select("SELECT * FROM member")
    List<Member> findAll();
}
