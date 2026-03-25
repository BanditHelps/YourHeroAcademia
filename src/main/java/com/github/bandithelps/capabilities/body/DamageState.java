package com.github.bandithelps.capabilities.body;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public enum DamageState {
    HEALTHY,
    SPRAINED,
    BROKEN,
    DESTROYED;

    public static DamageState fromHealth(float currentHealth, float maxHealth) {
        float clampedMax = Math.max(1.0F, maxHealth);
        float clampedCurrent = Math.max(0.0F, Math.min(currentHealth, clampedMax));
        float percent = clampedCurrent / clampedMax;
        if (percent <= 0.0F) {
            return DESTROYED;
        }
        if (percent < 0.50F) {
            return BROKEN;
        }
        if (percent < 0.80F) {
            return SPRAINED;
        }
        return HEALTHY;
    }

    public static DamageState fromId(String id) {
        return switch(id) {
            case "destroyed" -> DESTROYED;
            case "broken" -> BROKEN;
            case "sprained" -> SPRAINED;
            case "healthy" -> HEALTHY;
            default -> null;
        };
    }

    /**
     * Used to reference inside the documentation the valid parts
     * @return
     */
    public static Collection<String> exampleValues() {
        List<String> parts = new ArrayList();
        parts.add("healthy");
        parts.add("sprained");
        parts.add("broken");
        parts.add("destroyed");
        return parts;
    }
}
