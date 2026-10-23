package com.papaya.claimworkflow.entity;

import com.papaya.claimworkflow.enums.ActionType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ActionTypeConverter implements AttributeConverter<ActionType, String> {
    @Override
    public String convertToDatabaseColumn(ActionType action) {
        return action == null ? null : action.value();
    }

    @Override
    public ActionType convertToEntityAttribute(String value) {
        if (value == null) return null;
        for (ActionType action : ActionType.values()) {
            if (action.value().equals(value)) return action;
        }
        throw new IllegalArgumentException("Unknown action type: " + value);
    }
}
