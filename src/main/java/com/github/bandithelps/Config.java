package com.github.bandithelps;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

// An example config class. This is not required, but it's a good idea to have one to keep your config organized.
// Demonstrates how to use Neo's config APIs
public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue LOG_DIRT_BLOCK = BUILDER
            .comment("Whether to log the dirt block on common setup")
            .define("logDirtBlock", true);

    public static final ModConfigSpec.IntValue MAGIC_NUMBER = BUILDER
            .comment("A magic number")
            .defineInRange("magicNumber", 42, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.ConfigValue<String> MAGIC_NUMBER_INTRODUCTION = BUILDER
            .comment("What you want the introduction message to be for the magic number")
            .define("magicNumberIntroduction", "The magic number is... ");

    // a list of strings that are treated as resource locations for items
    public static final ModConfigSpec.ConfigValue<List<? extends String>> ITEM_STRINGS = BUILDER
            .comment("A list of items to log on common setup.")
            .defineListAllowEmpty("items", List.of("minecraft:iron_ingot"), () -> "", Config::validateItemName);

    public static final ModConfigSpec.IntValue CLOUD_SIM_INTERVAL_TICKS = BUILDER
            .comment("How often cloud simulation steps run. Lower is smoother but heavier.")
            .defineInRange("cloud.simulationIntervalTicks", 2, 1, 20);

    public static final ModConfigSpec.IntValue CLOUD_MAX_ACTIVE_VOLUMES = BUILDER
            .comment("Maximum number of cloud volumes that may exist per level.")
            .defineInRange("cloud.maxActiveVolumes", 32, 1, 512);

    public static final ModConfigSpec.IntValue CLOUD_MAX_CELLS_PER_VOLUME = BUILDER
            .comment("Maximum active cells per cloud volume.")
            .defineInRange("cloud.maxCellsPerVolume", 4096, 16, 200000);

    public static final ModConfigSpec.IntValue CLOUD_MAX_CELL_CHANGES_PER_TICK = BUILDER
            .comment("Maximum number of diffuse propagation operations per simulation step.")
            .defineInRange("cloud.maxCellChangesPerTick", 512, 16, 500000);

    public static final ModConfigSpec.IntValue CLOUD_MAX_FLOODFILL_EXPANSIONS_PER_TICK = BUILDER
            .comment("Maximum flood-fill expansions per simulation step.")
            .defineInRange("cloud.maxFloodFillExpansionsPerTick", 128, 8, 100000);

    public static final ModConfigSpec.DoubleValue CLOUD_PASSIVE_DECAY_PER_STEP = BUILDER
            .comment("Density removed from each cell on every simulation step.")
            .defineInRange("cloud.passiveDecayPerStep", 0.0035D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue CLOUD_DIFFUSION_FACTOR = BUILDER
            .comment("Fraction of density a cell attempts to diffuse to neighbors each step.")
            .defineInRange("cloud.diffusionFactor", 0.15D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue CLOUD_DEFAULT_LIFETIME_TICKS = BUILDER
            .comment("Default cloud lifetime used by abilities when unspecified.")
            .defineInRange("cloud.defaultLifetimeTicks", 20 * 60, 20, 20 * 60 * 30);

    public static final ModConfigSpec.IntValue CLOUD_SYNC_DISTANCE_BLOCKS = BUILDER
            .comment("Maximum player distance for server cloud sync packets.")
            .defineInRange("cloud.syncDistanceBlocks", 96, 16, 512);

    public static final ModConfigSpec.IntValue CLOUD_CLIENT_PARTICLE_BUDGET = BUILDER
            .comment("Maximum cloud particles spawned per client tick.")
            .defineInRange("cloud.clientParticleBudget", 180, 0, 4000);

    public static final ModConfigSpec.DoubleValue CLOUD_CLIENT_PARTICLE_DISTANCE = BUILDER
            .comment("Maximum distance for cloud particle rendering on clients.")
            .defineInRange("cloud.clientParticleDistance", 48.0D, 8.0D, 256.0D);

    public static final ModConfigSpec.IntValue CLOUD_MANAGED_MAX_ACTIVE_PARTICLES = BUILDER
            .comment("Maximum managed smoke particles that can be active on a client.")
            .defineInRange("cloud.managed.maxActiveParticles", 4500, 0, 100000);

    public static final ModConfigSpec.IntValue CLOUD_MANAGED_MAX_UPDATES_PER_TICK = BUILDER
            .comment("Maximum managed particles updated each client tick.")
            .defineInRange("cloud.managed.maxUpdatesPerTick", 3500, 0, 100000);

    public static final ModConfigSpec.IntValue CLOUD_MANAGED_DISPERSE_IMPULSE_TICKS = BUILDER
            .comment("How many ticks dispersal impulse continues to affect managed particles.")
            .defineInRange("cloud.managed.disperseImpulseTicks", 10, 1, 200);

    public static final ModConfigSpec.DoubleValue CLOUD_MANAGED_DISPERSE_IMPULSE_DAMPING = BUILDER
            .comment("Velocity damping applied to dispersal impulse each tick.")
            .defineInRange("cloud.managed.disperseImpulseDamping", 0.84D, 0.0D, 1.0D);

    public static final ModConfigSpec.DoubleValue CLOUD_MANAGED_DISPERSE_DRAG = BUILDER
            .comment("Drag used by managed particles while dispersal impulse is active.")
            .defineInRange("cloud.managed.disperseDrag", 0.94D, 0.0D, 1.0D);

    public static final ModConfigSpec.IntValue CLOUD_MANAGED_DISPERSE_LIFETIME_TICKS = BUILDER
            .comment("Maximum remaining lifetime for particles that get dispersed.")
            .defineInRange("cloud.managed.disperseLifetimeTicks", 60, 1, 6000);

    public static final ModConfigSpec.DoubleValue CLOUD_MANAGED_DISPERSE_TRIGGER_DENSITY_DROP = BUILDER
            .comment("Minimum client-observed density drop required to trigger dispersal impulse.")
            .defineInRange("cloud.managed.disperseTriggerDensityDrop", 0.03D, 0.01D, 1.0D);

    public static final ModConfigSpec.DoubleValue CLOUD_MANAGED_DISPERSE_IMPULSE_STRENGTH = BUILDER
            .comment("Base strength multiplier applied to inferred dispersal impulses.")
            .defineInRange("cloud.managed.disperseImpulseStrength", 0.08D, 0.001D, 4.0D);

    public static final ModConfigSpec.IntValue CLOUD_MANAGED_DISPERSE_SPAWN_SUPPRESS_TICKS = BUILDER
            .comment("How many client ticks cloud cells stop spawning particles after dispersal.")
            .defineInRange("cloud.managed.disperseSpawnSuppressTicks", 12, 0, 200);

    public static final ModConfigSpec.BooleanValue CLOUD_DEBUG_LOGGING = BUILDER
            .comment("Whether to periodically log cloud simulation metrics.")
            .define("cloud.debugLogging", false);

    static final ModConfigSpec SPEC = BUILDER.build();

    private static boolean validateItemName(final Object obj) {
        return obj instanceof String itemName && BuiltInRegistries.ITEM.containsKey(Identifier.parse(itemName));
    }
}
