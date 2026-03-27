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

    public static int managedMaxActiveParticles() {
        return Math.max(0, Config.CLOUD_MANAGED_MAX_ACTIVE_PARTICLES.get());
    }

    public static int managedMaxUpdatesPerTick() {
        return Math.max(0, Config.CLOUD_MANAGED_MAX_UPDATES_PER_TICK.get());
    }

    public static int managedDisperseImpulseTicks() {
        return Math.max(1, Config.CLOUD_MANAGED_DISPERSE_IMPULSE_TICKS.get());
    }

    public static double managedDisperseImpulseDamping() {
        return Math.max(0.0D, Math.min(1.0D, Config.CLOUD_MANAGED_DISPERSE_IMPULSE_DAMPING.get()));
    }

    public static float managedDisperseDrag() {
        return (float) Math.max(0.0D, Math.min(1.0D, Config.CLOUD_MANAGED_DISPERSE_DRAG.get()));
    }

    public static int managedDisperseLifetimeTicks() {
        return Math.max(1, Config.CLOUD_MANAGED_DISPERSE_LIFETIME_TICKS.get());
    }

    public static float managedDisperseTriggerDensityDrop() {
        return Math.max(0.01F, Config.CLOUD_MANAGED_DISPERSE_TRIGGER_DENSITY_DROP.get().floatValue());
    }

    public static double managedDisperseImpulseStrength() {
        return Math.max(0.001D, Config.CLOUD_MANAGED_DISPERSE_IMPULSE_STRENGTH.get());
    }

    public static int managedDisperseSpawnSuppressTicks() {
        return Math.max(0, Config.CLOUD_MANAGED_DISPERSE_SPAWN_SUPPRESS_TICKS.get());
    }

    public static boolean debugLogging() {
        return Config.CLOUD_DEBUG_LOGGING.get();
    }
}
