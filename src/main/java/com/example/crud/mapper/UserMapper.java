package com.example.crud.mapper;

import org.apache.catalina.User;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface UserMapper {
    @Select("SELECT * FROM users")
    List<User> findAll();
}
