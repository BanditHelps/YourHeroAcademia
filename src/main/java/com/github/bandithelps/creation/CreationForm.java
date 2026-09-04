package com.github.bandithelps.creation;

import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

public enum CreationForm {
    NUGGET,
    BASE,
    BLOCK;

    public int scaledCost(int baseCost) {
        int cost = Math.max(1, baseCost);
        return switch (this) {
            case NUGGET -> Math.max(1, Mth.ceil(cost / 9.0f));
            case BASE -> cost;
            case BLOCK -> Math.max(1, Mth.ceil(cost * 9.0f));
        };
    }

    public static CreationForm of(CreationEntry parent, Identifier requested) {
        if (parent == null || requested == null) {
            return BASE;
        }
        if (requested.equals(parent.nuggetId())) {
            return NUGGET;
        }
        if (requested.equals(parent.blockId())) {
            return BLOCK;
        }
        return BASE;
    }

    public static CreationForm of(String baseId, String nuggetId, String blockId, String requested) {
        if (requested != null && !requested.isBlank()) {
            if (requested.equals(nuggetId)) {
                return NUGGET;
            }
            if (requested.equals(blockId)) {
                return BLOCK;
            }
        }
        return BASE;
    }
}
