package com.arafawood.api.service;

import com.arafawood.api.domain.AppSetting;
import com.arafawood.api.repo.AppSettingRepository;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingService {

    private final AppSettingRepository settings;

    public SettingService(AppSettingRepository settings) {
        this.settings = settings;
    }

    @Transactional(readOnly = true)
    public Map<String, String> all() {
        Map<String, String> map = new LinkedHashMap<>();
        settings.findAll().forEach(row -> map.put(row.getKey(), row.getValue()));
        return map;
    }

    @Transactional(readOnly = true)
    public String get(String key, String fallback) {
        return settings.findById(key).map(AppSetting::getValue).orElse(fallback);
    }

    @Transactional(readOnly = true)
    public BigDecimal decimal(String key, String fallback) {
        return new BigDecimal(get(key, fallback));
    }

    @Transactional
    public void put(String key, String value) {
        AppSetting row = settings.findById(key).orElseGet(AppSetting::new);
        row.setKey(key);
        row.setValue(value);
        settings.save(row);
    }
}
