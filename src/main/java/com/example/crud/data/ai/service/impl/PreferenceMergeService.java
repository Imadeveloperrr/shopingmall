package com.example.crud.data.ai.service.impl;

import com.example.crud.data.ai.dto.Preference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.common.util.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;


@Service
@Slf4j
public class PreferenceMergeService {
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 두 JSON 문자열(HF와 ChatGPT 결과)을 Preference 객체로 파싱 후,
     * HF 결과를 우선으로 병합하여 Preference 객체를 반환합니다.
     */
    public Preference mergePreferences(String hfPreferenceJson, String chatGptPreferenceJson) {
        try {
            Preference hfPreference = objectMapper.readValue(hfPreferenceJson, Preference.class);
            Preference chatGptPreference = objectMapper.readValue(chatGptPreferenceJson, Preference.class);
            Preference merged = new Preference();
            merged.setCategory(StringUtils.isNotBlank(hfPreference.getCategory()) ? hfPreference.getCategory() : chatGptPreference.getCategory());
            merged.setStyle(StringUtils.isNotBlank(hfPreference.getStyle()) ? hfPreference.getStyle() : chatGptPreference.getStyle());
            merged.setColor(StringUtils.isNotBlank(hfPreference.getColor()) ? hfPreference.getColor() : chatGptPreference.getColor());
            merged.setSize(StringUtils.isNotBlank(hfPreference.getSize()) ? hfPreference.getSize() : chatGptPreference.getSize());
            log.info("병합된 선호 정보: {}", merged);
            return merged;
        } catch (Exception e) {
            log.error("Preference merge 실패: {}", e.getMessage(), e);
            throw new RuntimeException("Preference merge 실패: " + e.getMessage(), e);
        }
    }
}
