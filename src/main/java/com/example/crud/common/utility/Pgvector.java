package com.example.crud.common.utility;

import org.postgresql.util.PGobject;

import java.sql.SQLException;

/** float[] → PGvector SQL 객체 변환용 경량 유틸 */
public final class Pgvector {

    private Pgvector() {}

    public static PGobject toSqlArray(float[] vec) throws SQLException {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vec[i]);
        }
        sb.append(']');

        PGobject obj = new PGobject();
        obj.setType("vector");
        obj.setValue(sb.toString());
        return obj;
    }
}
