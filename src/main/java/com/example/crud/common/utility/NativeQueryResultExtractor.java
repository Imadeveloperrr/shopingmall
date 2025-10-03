package com.example.crud.common.utility;

public class NativeQueryResultExtractor {

    // 유틸리티 클래스는 인스턴스화 방지
    private NativeQueryResultExtractor() {
        throw new AssertionError("인스턴스화 ㄴㄴ");
    }

    /**
     * Object를 Long으로 변환
     */
    public static Long extractLong(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(String.format("필드 '%s'이(가) null입니다.", fieldName));
        }

        if (value instanceof Number number) {
            return number.longValue();
        }

        throw new IllegalArgumentException(String.format("필드 '%s'이(가) 숫자가 아닙니다. 실제 타입:%s, 값: %s",
                fieldName, value.getClass().getName(), value.toString()));
    }

    /**
     * Object를 String으로 변환
     */
    public static String extractString(Object value, String fieldName) {
        if (value == null) {
            return null; // description, description_vector는 null이니 임시로.
        }

        if (value instanceof String string) {
            return string;
        }

        return value.toString();
    }

    public static Double extractDouble(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(
                    String.format("필드 '%s'이(가) null입니다.", fieldName)
            );
        }

        if (value instanceof Number number) {
            return number.doubleValue();
        }

        throw new IllegalArgumentException(
                String.format("필드 '%s'이(가) 숫자가 아닙니다. 실제 타입: %s, 값: %s",
                        fieldName, value.getClass().getSimpleName(), value)
        );
    }

    public static Integer extractInteger(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(
                    String.format("필드 '%s'이(가) null입니다.", fieldName)
            );
        }

        if (value instanceof Number number) {
            return number.intValue();
        }

        throw new IllegalArgumentException(
                String.format("필드 '%s'이(가) 숫자가 아닙니다. 실제 타입: %s, 값: %s",
                        fieldName, value.getClass().getSimpleName(), value)
        );
    }

    public static Boolean extractBoolean(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(
                    String.format("필드 '%s'이(가) null입니다.", fieldName)
            );
        }

        if (value instanceof Boolean bool) {
            return bool;
        }

        // 숫자 → Boolean 변환 (0 = false, 1 = true)
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }

        throw new IllegalArgumentException(
                String.format("필드 '%s'을(를) Boolean으로 변환할 수 없습니다. 실제 타입: %s, 값: %s",
                        fieldName, value.getClass().getSimpleName(), value)
        );
    }
}
