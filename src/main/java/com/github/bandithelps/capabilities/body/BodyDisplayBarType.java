package com.github.bandithelps.capabilities.body;

import java.util.Locale;

public enum BodyDisplayBarType {
    FILL("fill"),
    SLIDER("slider");

    private final String id;

    BodyDisplayBarType(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public static BodyDisplayBarType fromId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.toLowerCase(Locale.ROOT);
        for (BodyDisplayBarType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return null;
    }
}
