package com.example.crud.common.helper;

import com.example.crud.common.exception.BaseException;
import com.example.crud.common.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * 컬렉션 검색 공통 로직
 * - stream().filter().findFirst().orElseThrow() 패턴을 제거
 */

@Component
public class CollectionHelper {

    /**
     * 컬렉션에서 조건에 맞는 첫 번쨰 요소 찾기
     *
     * @param collection 검색할 컬렉션
     * @param predicate  검색 조건
     * @param errorCode  찾지 못했을 때 던질 에러 코드
     * @param args       에러 메시지 파라미터
     * @return 찾은 요소
     * @throws BaseException 요소를 찾지 못한 경우
     */
    public <T> T findOrThrows(
            Collection<T> collection,
            Predicate<T> predicate,
            ErrorCode errorCode,
            Object... args) {

        return collection.stream()
                .filter(predicate)
                .findFirst()
                .orElseThrow(() -> new BaseException(errorCode, args));
    }

}
