package com.github.bandithelps.creation;

import java.util.Locale;

public enum CreationTab {
    MATERIALS,
    BLOCKS,
    GEAR;

    public static CreationTab fromId(String raw) {
        if (raw == null || raw.isBlank()) {
            return MATERIALS;
        }
        try {
            return CreationTab.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return MATERIALS;
        }
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
