package com.example.crud.common.utility;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;

@UtilityClass
public class Json {

    private static final ObjectMapper om = new ObjectMapper();

    public static String encode(Object obj) {
        try { return om.writeValueAsString(obj); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }

    public static <T> T decode(String json, Class<T> type) {
        try { return om.readValue(json, type); }
        catch (JsonProcessingException e) { throw new RuntimeException(e); }
    }
}
