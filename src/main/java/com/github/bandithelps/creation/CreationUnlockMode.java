package com.github.bandithelps.creation;

import java.util.Locale;

public enum CreationUnlockMode {
    ABILITY,
    WOOD;

    public static CreationUnlockMode fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return ABILITY;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return ABILITY;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
