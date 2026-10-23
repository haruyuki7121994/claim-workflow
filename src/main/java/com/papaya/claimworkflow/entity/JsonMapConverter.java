package com.papaya.claimworkflow.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.HashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

@Converter
public class JsonMapConverter implements AttributeConverter<Map<String, Object>, String> {
    // Hibernate snapshots converted values using a JSON round trip. Keep numeric
    // types stable so an unchanged version/amount does not look like an update.
    private static final JsonMapper MAPPER = JsonMapper.builder()
            .enable(DeserializationFeature.USE_LONG_FOR_INTS)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .build();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> value) {
        return MAPPER.writeValueAsString(value == null ? Map.of() : value);
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String value) {
        if (value == null || value.isBlank()) {
            return new HashMap<>();
        }
        return new HashMap<>(MAPPER.readValue(value, new TypeReference<Map<String, Object>>() { }));
    }
}
