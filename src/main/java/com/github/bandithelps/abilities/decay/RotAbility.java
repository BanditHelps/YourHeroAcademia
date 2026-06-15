package com.github.bandithelps.abilities.decay;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.utils.decay.DecayHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;
import net.minecraft.util.ExtraCodecs;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Rot Wave - sends a growing cone of rot in front of the user. Plant life withers (converting grass
 * to dirt and crumbling flowers/leaves/logs), and living entities inside the cone are afflicted with
 * the decay effect. Range, speed, and decay strength all scale with quirk factor.
 */
public class RotAbility extends Ability {

    private static final float QUIRK_SPEED_MULTIPLIER = 2.0f;
    private static final float QUIRK_RANGE_MULTIPLIER = 1.5f;
    private static final float MAX_EFFECTIVE_SPEED = 12.0f;
    private static final double CONE_HALF_ANGLE = Math.toRadians(30);

    private final Map<UUID, int[]> states = new ConcurrentHashMap<>(); // [distance, ticksSinceStart]

    private static final Set<Block> PLANT_BLOCKS = Set.of(
            Blocks.SHORT_GRASS, Blocks.TALL_GRASS, Blocks.FERN, Blocks.LARGE_FERN,
            Blocks.DANDELION, Blocks.POPPY, Blocks.BLUE_ORCHID, Blocks.ALLIUM,
            Blocks.AZURE_BLUET, Blocks.RED_TULIP, Blocks.ORANGE_TULIP, Blocks.WHITE_TULIP,
            Blocks.PINK_TULIP, Blocks.OXEYE_DAISY, Blocks.CORNFLOWER, Blocks.LILY_OF_THE_VALLEY,
            Blocks.SUNFLOWER, Blocks.LILAC, Blocks.ROSE_BUSH, Blocks.PEONY,
            Blocks.DEAD_BUSH, Blocks.WHEAT, Blocks.CARROTS, Blocks.POTATOES,
            Blocks.BEETROOTS, Blocks.SWEET_BERRY_BUSH, Blocks.BAMBOO, Blocks.SUGAR_CANE,
            Blocks.CACTUS, Blocks.BROWN_MUSHROOM, Blocks.RED_MUSHROOM,
            Blocks.VINE, Blocks.GLOW_LICHEN, Blocks.MOSS_CARPET, Blocks.MOSS_BLOCK, Blocks.MELON,
            Blocks.OAK_SAPLING, Blocks.SPRUCE_SAPLING, Blocks.BIRCH_SAPLING,
            Blocks.JUNGLE_SAPLING, Blocks.ACACIA_SAPLING, Blocks.DARK_OAK_SAPLING,
            Blocks.KELP, Blocks.KELP_PLANT, Blocks.SEAGRASS, Blocks.TALL_SEAGRASS
    );

    private static final Set<Block> WOOD_BLOCKS = Set.of(
            Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG,
            Blocks.JUNGLE_LOG, Blocks.ACACIA_LOG, Blocks.DARK_OAK_LOG,
            Blocks.CRIMSON_STEM, Blocks.WARPED_STEM,
            Blocks.OAK_WOOD, Blocks.SPRUCE_WOOD, Blocks.BIRCH_WOOD,
            Blocks.JUNGLE_WOOD, Blocks.ACACIA_WOOD, Blocks.DARK_OAK_WOOD
    );

    private static final Set<Block> LEAF_BLOCKS = Set.of(
            Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES,
            Blocks.JUNGLE_LEAVES, Blocks.ACACIA_LEAVES, Blocks.DARK_OAK_LEAVES,
            Blocks.AZALEA_LEAVES, Blocks.FLOWERING_AZALEA_LEAVES,
            Blocks.NETHER_WART_BLOCK, Blocks.WARPED_WART_BLOCK
    );

    public static final MapCodec<RotAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("max_radius", new StaticValue(12.0f)).forGetter((ab) -> ab.maxRadius),
                    Value.CODEC.optionalFieldOf("speed", new StaticValue(1.5f)).forGetter((ab) -> ab.speed),
                    Value.CODEC.optionalFieldOf("instability_per_interval", new StaticValue(2.0f)).forGetter((ab) -> ab.instabilityPerInterval),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("decay_effect_amplifier", 1).forGetter((ab) -> ab.decayEffectAmplifier),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, RotAbility::new));

    public final Value maxRadius;
    public final Value speed;
    public final Value instabilityPerInterval;
    public final int decayEffectAmplifier;

    public RotAbility(Value maxRadius, Value speed, Value instabilityPerInterval, int decayEffectAmplifier, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.maxRadius = maxRadius;
        this.speed = speed;
        this.instabilityPerInterval = instabilityPerInterval;
        this.decayEffectAmplifier = decayEffectAmplifier;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            this.states.put(player.getUUID(), new int[]{0, 0});
            level.playSound(null, player.blockPosition(), SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.3f, 1.4f);
        }
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level) {
            int[] state = this.states.computeIfAbsent(player.getUUID(), id -> new int[]{0, 0});
            state[1]++;
            try {
                executeRot(player, level, state, DataContext.forEntity(entity));
            } catch (Exception ignored) {
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            this.states.remove(player.getUUID());
            if (player.level() instanceof ServerLevel level) {
                level.playSound(null, player.blockPosition(), SoundEvents.WITHER_DEATH, SoundSource.PLAYERS, 0.4f, 1.3f);
            }
        }
    }

    private void executeRot(ServerPlayer player, ServerLevel level, int[] state, DataContext context) {
        double quirkFactor = QuirkFactorUtil.getQuirkFactor(player);
        int baseMaxRadius = Math.max(1, this.maxRadius.getAsInt(context));
        float baseSpeed = this.speed.getAsFloat(context);

        int currentRadius = state[0];
        int ticksSinceStart = state[1];

        float effSpeed = Math.min(baseSpeed * (1.0f + (float) quirkFactor * QUIRK_SPEED_MULTIPLIER), MAX_EFFECTIVE_SPEED);
        int effMaxRadius = baseMaxRadius + (int) (quirkFactor * QUIRK_RANGE_MULTIPLIER * baseMaxRadius);
        int ticksPerExpansion = Math.max(1, (int) (5 / Math.max(0.05f, effSpeed)));
        int expectedRadius = Math.min(1 + (ticksSinceStart / ticksPerExpansion), effMaxRadius);

        if (expectedRadius > currentRadius) {
            state[0] = expectedRadius;
            performRotWave(player, level, expectedRadius, currentRadius, quirkFactor);

            if (ticksSinceStart % 20 == 0) {
                float instab = this.instabilityPerInterval.getAsFloat(context);
                if (instab > 0.0f) {
                    DecayHelper.addInstability(player, instab);
                }
            }
        }
    }

    private void performRotWave(ServerPlayer player, ServerLevel level, int newRadius, int currentRadius, double quirkFactor) {
        BlockPos centerPos = player.blockPosition();
        Vec3 look = player.getLookAngle();
        double lookX = look.x;
        double lookZ = look.z;
        double lookLength = Math.sqrt(lookX * lookX + lookZ * lookZ);
        if (lookLength > 0) {
            lookX /= lookLength;
            lookZ /= lookLength;
        }

        int blocksProcessed = 0;
        int maxBlocksPerTick = 250;
        Set<BlockPos> processed = new HashSet<>();
        int startRadius = Math.max(1, currentRadius);
        int searchSize = newRadius + 2;

        for (int x = centerPos.getX() - searchSize; x <= centerPos.getX() + searchSize && blocksProcessed < maxBlocksPerTick; x++) {
            for (int z = centerPos.getZ() - searchSize; z <= centerPos.getZ() + searchSize && blocksProcessed < maxBlocksPerTick; z++) {
                double dx = x - centerPos.getX();
                double dz = z - centerPos.getZ();
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance < startRadius || distance > newRadius) continue;

                if (distance > 0.5) {
                    double dot = (dx * lookX + dz * lookZ) / distance;
                    double angle = Math.acos(Math.max(-1.0, Math.min(1.0, dot)));
                    if (angle > CONE_HALF_ANGLE) continue;
                }

                if (processRotColumn(level, new BlockPos(x, centerPos.getY(), z), processed)) {
                    blocksProcessed++;
                }
            }
        }

        applyDecayToEntities(player, level, centerPos, newRadius, lookX, lookZ, quirkFactor);

        if (newRadius > currentRadius) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, centerPos.getX(), centerPos.getY() + 1, centerPos.getZ(), 2, 1, 0.5, 1, 0.1);
            level.sendParticles(ParticleTypes.ASH, centerPos.getX(), centerPos.getY() + 1, centerPos.getZ(), 4, 2, 1, 2, 0.1);
            level.playSound(null, centerPos, SoundEvents.WITHER_AMBIENT, SoundSource.PLAYERS, 0.5f, 0.8f);
        }
    }

    private boolean processRotColumn(ServerLevel level, BlockPos columnPos, Set<BlockPos> processed) {
        boolean processedAny = false;
        int surfaceY = findSurface(level, columnPos.getX(), columnPos.getZ(), columnPos.getY());
        int minY = Math.max(level.getMinY(), surfaceY - 3);
        int maxY = Math.min(level.getMaxY(), surfaceY + 15);

        for (int y = minY; y <= maxY; y++) {
            BlockPos pos = new BlockPos(columnPos.getX(), y, columnPos.getZ());
            if (!processed.add(pos)) continue;

            BlockState blockState = level.getBlockState(pos);
            Block block = blockState.getBlock();
            if (block == Blocks.AIR) continue;

            if (block == Blocks.GRASS_BLOCK) {
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 1, pos.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.05);
                processedAny = true;
            } else if (PLANT_BLOCKS.contains(block) || LEAF_BLOCKS.contains(block)) {
                if (LEAF_BLOCKS.contains(block) || WOOD_BLOCKS.contains(block)) {
                    propagateTreeDecay(level, pos, processed);
                }
                scheduleBlockDestroy(level, pos, ThreadLocalRandom.current().nextInt(10) + 1);
                processedAny = true;
            } else if (WOOD_BLOCKS.contains(block)) {
                propagateTreeDecay(level, pos, processed);
                scheduleBlockDestroy(level, pos, ThreadLocalRandom.current().nextInt(15) + 5);
                processedAny = true;
            }
        }
        return processedAny;
    }

    private int findSurface(ServerLevel level, int x, int z, int startY) {
        int scanStart = Math.min(level.getMaxY(), startY + 20);
        int scanEnd = Math.max(level.getMinY(), startY - 40);
        for (int y = scanStart; y >= scanEnd; y--) {
            BlockState blockState = level.getBlockState(new BlockPos(x, y, z));
            Block block = blockState.getBlock();
            if (block == Blocks.AIR || PLANT_BLOCKS.contains(block) || LEAF_BLOCKS.contains(block)
                    || block == Blocks.SNOW || block == Blocks.POWDER_SNOW) {
                continue;
            }
            return y;
        }
        return startY;
    }

    private void propagateTreeDecay(ServerLevel level, BlockPos startPos, Set<BlockPos> processed) {
        Queue<BlockPos> toProcess = new LinkedList<>();
        Set<BlockPos> visited = new HashSet<>();
        toProcess.offer(startPos);
        visited.add(startPos);

        int maxBlocks = 60;
        int count = 0;
        while (!toProcess.isEmpty() && count < maxBlocks) {
            BlockPos current = toProcess.poll();
            count++;
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos neighbor = current.offset(dx, dy, dz);
                        if (visited.contains(neighbor) || processed.contains(neighbor)) continue;

                        Block block = level.getBlockState(neighbor).getBlock();
                        if (WOOD_BLOCKS.contains(block) || LEAF_BLOCKS.contains(block)) {
                            visited.add(neighbor);
                            toProcess.offer(neighbor);
                            processed.add(neighbor);
                            scheduleBlockDestroy(level, neighbor, ThreadLocalRandom.current().nextInt(20) + count);
                        }
                    }
                }
            }
        }
    }

    private void applyDecayToEntities(ServerPlayer caster, ServerLevel level, BlockPos centerPos, int radius, double lookX, double lookZ, double quirkFactor) {
        AABB searchArea = new AABB(
                centerPos.getX() - radius, centerPos.getY() - 4, centerPos.getZ() - radius,
                centerPos.getX() + radius, centerPos.getY() + 8, centerPos.getZ() + radius);

        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchArea)) {
            if (entity == caster) continue;

            double dx = entity.getX() - centerPos.getX();
            double dz = entity.getZ() - centerPos.getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance > radius) continue;

            if (distance > 1) {
                double entityAngle = Math.atan2(dz, dx);
                double lookAngle = Math.atan2(lookZ, lookX);
                double angleDiff = Math.abs(entityAngle - lookAngle);
                if (angleDiff > Math.PI) angleDiff = 2 * Math.PI - angleDiff;
                if (angleDiff > CONE_HALF_ANGLE) continue;
            }

            double proximity = 1.0 - (distance / Math.max(1, radius));
            int duration = (int) (80 + proximity * 120);
            int baseAmp = this.decayEffectAmplifier + (int) Math.floor(proximity * 2);
            DecayHelper.applyDecayEffect(level, entity, quirkFactor, baseAmp, duration);
        }
    }

    private void scheduleBlockDestroy(ServerLevel level, BlockPos pos, int delayTicks) {
        // The cone already expands gradually wave-by-wave, so the destruction itself is applied
        // immediately (the delayTicks parameter is retained for call-site readability/tuning).
        BlockPos immutable = pos.immutable();
        level.destroyBlock(immutable, false);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, immutable.getX() + 0.5, immutable.getY() + 0.5, immutable.getZ() + 0.5, 3, 0.3, 0.3, 0.3, 0.1);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.ROT_WAVE.get();
    }

    public static class Serializer extends AbilitySerializer<RotAbility> {
        public MapCodec<RotAbility> codec() {
            return RotAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, RotAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("While held, projects a growing cone of rot that withers plant life and afflicts entities in the cone with the decay effect. Range, speed, and strength scale with quirk factor.")
                    .add("max_radius", TYPE_VALUE, "Base maximum radius of the cone (scaled up by quirk factor).")
                    .add("speed", TYPE_VALUE, "Base expansion speed of the cone (scaled up by quirk factor).")
                    .add("instability_per_interval", TYPE_VALUE, "Instability gained per second while active.")
                    .add("decay_effect_amplifier", TYPE_INT, "Base amplifier of the decay effect applied to entities.")
                    .addExampleObject(new RotAbility(new StaticValue(12.0f), new StaticValue(1.5f), new StaticValue(2.0f), 1, AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
