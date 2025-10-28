package com.example.crud.data.member.converter;

import com.example.crud.data.member.dto.response.MemberResponse;
import com.example.crud.entity.Member;
import org.mapstruct.Mapper;

/**
 * Member Entity → DTO 변환기 (MapStruct)
 *
 * 설계:
 * - 읽기 전용 변환만 담당
 * - 필드명 1:1 매핑 시 @Mapping 생략 (MapStruct 자동 처리)
 *
 * 네이밍:
 * - MyBatis: MemberMapper (common.mapper)
 * - MapStruct: MemberConverter (data.member.converter)
 */
@Mapper(componentModel = "spring")
public interface MemberConverter {

    /**
     * Entity → Response DTO
     *
     * MapStruct가 자동으로 매핑:
     * - member.getNumber() → response.number()
     * - member.getEmail() → response.email()
     * - ...
     */
    MemberResponse toResponse(Member member);
}
