package com.github.bandithelps.client.particles.managed;

import net.minecraft.util.Mth;

public final class ClientManagedParticleSettings {
    private static final float MIN_SIZE = 0.05F;
    private static final float MAX_SIZE = 6.0F;

    private static volatile float cloudSmokeSize = 1.0F;
    private static volatile float beamSmokeSize = 1.0F;

    private ClientManagedParticleSettings() {
    }

    public static float cloudSmokeSize() {
        return cloudSmokeSize;
    }

    public static float beamSmokeSize() {
        return beamSmokeSize;
    }

    public static void setCloudSmokeSize(float size) {
        cloudSmokeSize = clamp(size);
    }

    public static void setBeamSmokeSize(float size) {
        beamSmokeSize = clamp(size);
    }

    public static void setAll(float size) {
        float clamped = clamp(size);
        cloudSmokeSize = clamped;
        beamSmokeSize = clamped;
    }

    private static float clamp(float size) {
        return Mth.clamp(size, MIN_SIZE, MAX_SIZE);
    }
}
