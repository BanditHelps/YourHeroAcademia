package com.github.bandithelps.cloud;

import com.github.bandithelps.Config;

public final class CloudSimConfig {
    private CloudSimConfig() {
    }

    public static int simulationIntervalTicks() {
        return Math.max(1, Config.CLOUD_SIM_INTERVAL_TICKS.get());
    }

    public static int maxActiveVolumes() {
        return Math.max(1, Config.CLOUD_MAX_ACTIVE_VOLUMES.get());
    }

    public static int maxCellsPerVolume() {
        return Math.max(16, Config.CLOUD_MAX_CELLS_PER_VOLUME.get());
    }

    public static int maxCellChangesPerTick() {
        return Math.max(16, Config.CLOUD_MAX_CELL_CHANGES_PER_TICK.get());
    }

    public static int maxFloodFillExpansionsPerTick() {
        return Math.max(8, Config.CLOUD_MAX_FLOODFILL_EXPANSIONS_PER_TICK.get());
    }

    public static float passiveDecayPerStep() {
        return Math.max(0.0F, Config.CLOUD_PASSIVE_DECAY_PER_STEP.get().floatValue());
    }

    public static float diffusionFactor() {
        return Math.max(0.0F, Config.CLOUD_DIFFUSION_FACTOR.get().floatValue());
    }

    public static int defaultLifetimeTicks() {
        return Math.max(20, Config.CLOUD_DEFAULT_LIFETIME_TICKS.get());
    }

    public static int minTrackingDistance() {
        return Math.max(16, Config.CLOUD_SYNC_DISTANCE_BLOCKS.get());
    }

    public static int maxParticleSpawnPerTick() {
        return Math.max(0, Config.CLOUD_CLIENT_PARTICLE_BUDGET.get());
    }

    public static double clientParticleDistance() {
        return Math.max(8.0D, Config.CLOUD_CLIENT_PARTICLE_DISTANCE.get());
    }

    public static boolean debugLogging() {
        return Config.CLOUD_DEBUG_LOGGING.get();
    }
}
