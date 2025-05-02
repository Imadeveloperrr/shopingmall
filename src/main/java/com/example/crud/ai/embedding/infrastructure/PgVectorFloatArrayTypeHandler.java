package com.example.crud.ai.embedding.infrastructure;

import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.sql.*;

public class PgVectorFloatArrayTypeHandler extends BaseTypeHandler<float[]> {

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i,
                                    float[] parameter, JdbcType jdbcType) throws SQLException {
        PGobject obj = new PGobject();
        obj.setType("vector");
        obj.setValue(arrayToSql(parameter));   // "{0.1,0.2,...}"
        ps.setObject(i, obj);
    }

    @Override
    public float[] getNullableResult(ResultSet rs, String columnName) throws SQLException {
        String val = rs.getString(columnName);
        return sqlToArray(val);
    }

    @Override
    public float[] getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return sqlToArray(rs.getString(columnIndex));
    }

    @Override
    public float[] getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return sqlToArray(cs.getString(columnIndex));
    }

    // ---------- util ----------
    private static String arrayToSql(float[] arr) {
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(arr[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static float[] sqlToArray(String src) {
        if (src == null) return null;
        String body = src.replaceAll("[\\[\\]]", "");   // "[0.1,0.2]" → "0.1,0.2"
        String[] parts = body.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Float.parseFloat(parts[i]);
        return out;
    }
}