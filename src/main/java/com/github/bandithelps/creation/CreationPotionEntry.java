package com.github.bandithelps.creation;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public record CreationPotionEntry(
        Identifier effectId,
        String abilityKey,
        Identifier groupId,
        Identifier groupIcon,
        int lipidCost,
        int lipidCostPerAmplifier,
        int researchCost,
        int maxDurationSeconds,
        Boolean instantOverride,
        Float experientialChance
) {
    public static final int DEFAULT_DURATION_SECONDS = 15;
    public static final int DEFAULT_MAX_DURATION_SECONDS = 480;

    public int durationCostSeconds(int durationSeconds) {
        if (durationSeconds <= 0) {
            return DEFAULT_DURATION_SECONDS;
        }
        return Mth.clamp(durationSeconds, 1, Math.max(1, this.maxDurationSeconds));
    }

    public int lipidCostFor(int extraAmplifier, int durationSeconds, CreationPotionForm form) {
        int extra = Math.max(0, extraAmplifier);
        CreationPotionForm resolved = form != null ? form : CreationPotionForm.DRINKABLE;
        double durationFactor = 1.0;
        if (!Boolean.TRUE.equals(this.instantOverride)) {
            durationFactor = Math.max(1.0, durationCostSeconds(durationSeconds) / (double) DEFAULT_DURATION_SECONDS);
        }
        return Math.max(1, (int) Math.ceil((this.lipidCost + extra * this.lipidCostPerAmplifier) * durationFactor * resolved.factor()));
    }
}
