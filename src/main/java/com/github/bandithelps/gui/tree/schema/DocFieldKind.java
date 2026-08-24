package com.github.bandithelps.gui.tree.schema;

import java.util.Locale;

public enum DocFieldKind {
    BOOLEAN,
    INTEGER,
    FLOAT,
    STRING,
    IDENTIFIER,
    ENUM,
    COMBINED,
    VALUE,
    CONDITION,
    ACTION,
    ITEM,
    ICON,
    COLOR,
    VEC2,
    VEC3,
    ABILITY_REF,
    STRING_LIST,
    RAW_JSON;

    public static DocFieldKind classify(String typeText) {
        if (typeText == null || typeText.isBlank()) {
            return RAW_JSON;
        }
        String lower = typeText.toLowerCase(Locale.ROOT);
        if (lower.contains("condition")) {
            return CONDITION;
        }
        if (lower.contains("action")) {
            return ACTION;
        }
        if (lower.contains("value")) {
            return VALUE;
        }
        if (lower.contains("boolean")) {
            return BOOLEAN;
        }
        if (lower.contains("icon")) {
            return ICON;
        }
        if (lower.contains("ingredient") || lower.contains("item")) {
            return ITEM;
        }
        if (lower.contains("ability reference") || lower.contains("ability ref")) {
            return ABILITY_REF;
        }
        if (lower.contains("color")) {
            return COLOR;
        }
        if (lower.contains("vector 2") || lower.contains("vec2")) {
            return VEC2;
        }
        if (lower.contains("vector 3") || lower.contains("vec3")) {
            return VEC3;
        }
        if (lower.contains("string[]") || lower.contains("string array")) {
            return STRING_LIST;
        }
        if (lower.contains("identifier") || lower.contains("texture reference") || lower.contains(" attachment id")
                || lower.contains("entity type") || lower.contains("sound") || lower.contains("particle type")
                || lower.contains("dimension id") || lower.contains("flight type") || lower.contains("damage type")
                || lower.contains("shader") || lower.contains("power id")) {
            return IDENTIFIER;
        }
        if (lower.contains("integer")) {
            return INTEGER;
        }
        if (lower.contains("float") || lower.contains("double") || lower.contains("number")) {
            return classifyNumber(lower);
        }
        if (lower.contains("molang") || lower.contains("string") || lower.contains("text")) {
            return STRING;
        }
        if (lower.contains("enum")) {
            return ENUM;
        }
        return RAW_JSON;
    }

    private static DocFieldKind classifyNumber(String lower) {
        if (lower.contains("integer")) {
            return INTEGER;
        }
        return FLOAT;
    }
}
