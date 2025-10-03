package com.example.crud.ai.common;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;


/**
 * 벡터 데이터와 PostgreSQL vector 형식 문자열 간 변환 유틸리티
 */
public class VectorFormatter {

    private static final DecimalFormat VECTOR_FORMAT;

    static {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.US);
        VECTOR_FORMAT = new DecimalFormat("0.########", symbols);
        VECTOR_FORMAT.setGroupingUsed(false);
    }

    // 인스턴스화 방지
    private VectorFormatter() {
        throw new AssertionError("VectorFormatter는 인스턴스화 할 수 없습니다.");
    }

    /**
     * float 배열을 PostgreSQL vector 형식 문자열로 변환
     */
    public static String formatForPostgreSQL(float[] vector) {
        if (vector == null) {
            throw new IllegalArgumentException("벡터가 NULL입니다.");
        }
        if (vector.length == 0) {
            throw new IllegalArgumentException("벡터가 비어있습니다.");
        }
        StringBuilder sb = new StringBuilder(vector.length * 12);
        sb.append("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(VECTOR_FORMAT.format(vector[i]));
        }
        sb.append("]");
        return sb.toString();
    }
}
