package com.example.crud.data.ai.service;

import com.example.crud.data.ai.dto.Preference;

public interface PreferenceMergeService {

    Preference mergePreferences(String hfPreferenceJson, String chatGptPreferenceJson);

}
