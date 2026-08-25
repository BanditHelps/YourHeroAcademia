package com.github.bandithelps.creation;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public record CreationEnchantEntry(
        Identifier enchantId,
        String abilityKey,
        int lipidCostPerLevel,
        int[] lipidCosts,
        int researchCost,
        Integer maxLevelOverride
) {
    public int lipidCostForLevel(int level) {
        if (level <= 0) {
            return 0;
        }
        if (lipidCosts != null && lipidCosts.length > 0) {
            int index = Mth.clamp(level, 1, lipidCosts.length) - 1;
            return Math.max(1, lipidCosts[index]);
        }
        return Math.max(1, lipidCostPerLevel * level);
    }

    public int resolvedMaxLevel(int vanillaMax) {
        int vanilla = Math.max(1, vanillaMax);
        if (maxLevelOverride == null) {
            return vanilla;
        }
        return Mth.clamp(maxLevelOverride, 1, vanilla);
    }
}
