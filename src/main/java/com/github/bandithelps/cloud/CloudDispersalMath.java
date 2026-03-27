package com.github.bandithelps.cloud;

import net.minecraft.util.Mth;

public final class CloudDispersalMath {
    private CloudDispersalMath() {
    }

    public static float radialFalloff(double distance, double radius) {
        if (radius <= 0.0D) {
            return 0.0F;
        }
        return Mth.clamp(1.0F - (float) (distance / radius), 0.0F, 1.0F);
    }
}
