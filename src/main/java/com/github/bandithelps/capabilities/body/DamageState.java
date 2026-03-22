package com.github.bandithelps.capabilities.body;

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
}
