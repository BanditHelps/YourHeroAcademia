package com.github.bandithelps.abilities.decay;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.IBodyData;
import com.github.bandithelps.utils.decay.DecayHelper;
import com.github.bandithelps.utils.quirk.QuirkFactorUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Environment Decay - the user crumbles the terrain in front of them. Rather than instantly
 * deleting blocks (as the legacy implementation did), this uses Minecraft's native block-breaking
 * progress system: cracks appear and grow across the target area in a chosen pattern, making the
 * world look like it is slowly rotting away. By default the blocks never fully break - the crack
 * progress fades back out once the user stops - but a sufficiently powerful user (high quirk factor)
 * or a purchased upgrade allows the decay to fully consume blocks (no drops).
 *
 * Living entities standing on/near decaying blocks are afflicted with the decay effect.
 */
public class EnvironmentDecayAbility extends Ability {

    private static final float QUIRK_SPEED_MULTIPLIER = 1.0f;
    private static final float QUIRK_BLOCKS_MULTIPLIER = 1.5f;
    private static final float QUIRK_INTENSITY_MULTIPLIER = 1.0f;
    private static final float MAX_EFFECTIVE_SPEED = 8.0f;

    // Each cracking block needs its own breaker id so multiple cracks render simultaneously.
    private static final AtomicInteger NEXT_BREAKER_ID = new AtomicInteger(500_000);

    // Per-instance (i.e. per JSON pattern entry) runtime state, keyed by player. Kept per-instance so
    // that disabled pattern variants don't fade the cracks created by the active one.
    private final Map<UUID, DecayState> states = new ConcurrentHashMap<>();

    public static final MapCodec<EnvironmentDecayAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Codec.STRING.optionalFieldOf("decay_type", "all").forGetter((ab) -> ab.decayType),
                    Value.CODEC.optionalFieldOf("range", new StaticValue(6.0f)).forGetter((ab) -> ab.range),
                    Value.CODEC.optionalFieldOf("spread_speed", new StaticValue(1.0f)).forGetter((ab) -> ab.spreadSpeed),
                    Value.CODEC.optionalFieldOf("max_blocks", new StaticValue(60.0f)).forGetter((ab) -> ab.maxBlocks),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("base_intensity", 2).forGetter((ab) -> ab.baseIntensity),
                    Value.CODEC.optionalFieldOf("crack_speed", new StaticValue(0.05f)).forGetter((ab) -> ab.crackSpeed),
                    Value.CODEC.optionalFieldOf("fade_speed", new StaticValue(0.08f)).forGetter((ab) -> ab.fadeSpeed),
                    Value.CODEC.optionalFieldOf("instability_per_interval", new StaticValue(2.0f)).forGetter((ab) -> ab.instabilityPerInterval),
                    Value.CODEC.optionalFieldOf("break_quirk_factor", new StaticValue(6.0f)).forGetter((ab) -> ab.breakQuirkFactor),
                    Codec.BOOL.optionalFieldOf("allow_break", false).forGetter((ab) -> ab.allowBreak),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("decay_effect_amplifier", 0).forGetter((ab) -> ab.decayEffectAmplifier),
                    Codec.BOOL.optionalFieldOf("read_pattern_from_body", true).forGetter((ab) -> ab.readPatternFromBody),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, EnvironmentDecayAbility::new));

    public final String decayType;
    public final Value range;
    public final Value spreadSpeed;
    public final Value maxBlocks;
    public final int baseIntensity;
    public final Value crackSpeed;
    public final Value fadeSpeed;
    public final Value instabilityPerInterval;
    public final Value breakQuirkFactor;
    public final boolean allowBreak;
    public final int decayEffectAmplifier;
    public final boolean readPatternFromBody;

    public EnvironmentDecayAbility(String decayType, Value range, Value spreadSpeed, Value maxBlocks, int baseIntensity,
                                   Value crackSpeed, Value fadeSpeed, Value instabilityPerInterval, Value breakQuirkFactor,
                                   boolean allowBreak, int decayEffectAmplifier, boolean readPatternFromBody,
                                   AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.decayType = decayType;
        this.range = range;
        this.spreadSpeed = spreadSpeed;
        this.maxBlocks = maxBlocks;
        this.baseIntensity = baseIntensity;
        this.crackSpeed = crackSpeed;
        this.fadeSpeed = fadeSpeed;
        this.instabilityPerInterval = instabilityPerInterval;
        this.breakQuirkFactor = breakQuirkFactor;
        this.allowBreak = allowBreak;
        this.decayEffectAmplifier = decayEffectAmplifier;
        this.readPatternFromBody = readPatternFromBody;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }

        double quirkFactor = QuirkFactorUtil.getQuirkFactor(player);
        int intensity = effectiveIntensity(quirkFactor);
        BlockPos target = findTargetBlock(player, this.range.getAsFloat(DataContext.forEntity(entity)));

        if (target == null || !isDecayable(level.getBlockState(target), intensity)) {
            return;
        }

        DecayState state = new DecayState();
        state.target = target;
        state.originalBlock = blockKey(level.getBlockState(target));
        state.pattern = resolvePattern(player);
        state.waveFront.add(target);
        addCrackingBlock(state, target);
        this.states.put(player.getUUID(), state);

        level.playSound(null, target, SoundEvents.WITHER_SPAWN, SoundSource.PLAYERS, 0.4f, 0.7f);
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (!(entity instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return super.tick(entity, abilityInstance, enabled);
        }

        DecayState state = this.states.get(player.getUUID());
        if (state == null) {
            return super.tick(entity, abilityInstance, enabled);
        }

        if (enabled) {
            activeTick(player, level, state, DataContext.forEntity(entity));
        } else {
            fadeTick(player, level, state, DataContext.forEntity(entity));
        }

        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        // Intentionally left empty - the cracks fade out gradually in fadeTick() rather than
        // disappearing the instant the key is released.
    }

    private void activeTick(ServerPlayer player, ServerLevel level, DecayState state, DataContext context) {
        double quirkFactor = QuirkFactorUtil.getQuirkFactor(player);
        int intensity = effectiveIntensity(quirkFactor);
        int effectiveMaxBlocks = (int) (this.maxBlocks.getAsFloat(context) * (1.0f + quirkFactor * QUIRK_BLOCKS_MULTIPLIER));

        state.ticksSinceStart++;

        // --- Wave expansion (grow the cracked region in the chosen pattern) ---
        float baseSpeed = this.spreadSpeed.getAsFloat(context);
        float effSpeed = Math.min(baseSpeed * (1.0f + (float) quirkFactor * QUIRK_SPEED_MULTIPLIER), MAX_EFFECTIVE_SPEED);
        int ticksPerWave = Math.max(1, (int) (15 / Math.max(0.05f, effSpeed)));
        int expectedWave = state.ticksSinceStart / ticksPerWave;
        if (expectedWave > state.currentWave && state.cracking.size() < effectiveMaxBlocks) {
            expandWave(level, state, intensity, effectiveMaxBlocks);
            state.currentWave = expectedWave;
        }

        // --- Crack growth ---
        float crackPerTick = this.crackSpeed.getAsFloat(context) * (1.0f + (float) quirkFactor * 0.5f);
        boolean canBreak = this.allowBreak || quirkFactor >= this.breakQuirkFactor.getAsFloat(context);
        advanceCracks(level, state, crackPerTick, canBreak);

        // --- Side effects: instability + decay on entities + particles ---
        if (state.ticksSinceStart % 20 == 0) {
            float instab = this.instabilityPerInterval.getAsFloat(context);
            if (instab > 0.0f) {
                DecayHelper.addInstability(player, instab);
            }
        }
        if (state.ticksSinceStart % 10 == 0) {
            applyDecayToEntities(level, state, quirkFactor);
        }
        spawnCrackParticles(level, state);
    }

    private void fadeTick(ServerPlayer player, ServerLevel level, DecayState state, DataContext context) {
        float fade = this.fadeSpeed.getAsFloat(context);
        List<BlockPos> toRemove = new ArrayList<>();

        for (Map.Entry<BlockPos, Float> entry : state.cracking.entrySet()) {
            float newProgress = entry.getValue() - fade;
            if (newProgress <= 0.0f) {
                toRemove.add(entry.getKey());
            } else {
                entry.setValue(newProgress);
                updateBreakStage(level, state, entry.getKey(), newProgress);
            }
        }

        for (BlockPos pos : toRemove) {
            clearCrackingBlock(level, state, pos);
        }

        if (state.cracking.isEmpty()) {
            this.states.remove(player.getUUID());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Wave / crack mechanics
    // ---------------------------------------------------------------------------------------------

    private void expandWave(ServerLevel level, DecayState state, int intensity, int maxBlocks) {
        Set<BlockPos> nextFront = new HashSet<>();
        for (BlockPos pos : state.waveFront) {
            if (state.cracking.size() >= maxBlocks) {
                break;
            }
            for (BlockPos neighbor : connectedBlocks(level, pos, state, intensity)) {
                if (state.cracking.size() >= maxBlocks) {
                    break;
                }
                if (!state.cracking.containsKey(neighbor) && !state.finished.contains(neighbor)
                        && isDecayable(level.getBlockState(neighbor), intensity)) {
                    addCrackingBlock(state, neighbor);
                    nextFront.add(neighbor);
                }
            }
        }
        state.waveFront = nextFront;
    }

    private void advanceCracks(ServerLevel level, DecayState state, float crackPerTick, boolean canBreak) {
        List<BlockPos> completed = new ArrayList<>();

        for (Map.Entry<BlockPos, Float> entry : state.cracking.entrySet()) {
            BlockPos pos = entry.getKey();
            float progress = Math.min(1.0f, entry.getValue() + crackPerTick);
            entry.setValue(progress);
            updateBreakStage(level, state, pos, progress);

            if (progress >= 1.0f) {
                if (canBreak) {
                    completed.add(pos);
                }
                // Otherwise: stays fully cracked but intact (stage clamped at max).
            }
        }

        for (BlockPos pos : completed) {
            level.destroyBlock(pos, false);
            level.sendParticles(ParticleTypes.LARGE_SMOKE,
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    6, 0.3, 0.3, 0.3, 0.05);
            level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.3f, 0.8f);
            clearCrackingBlock(level, state, pos);
            state.finished.add(pos);
            state.waveFront.add(pos); // allow the wave to continue spreading through the gap
        }
    }

    private void applyDecayToEntities(ServerLevel level, DecayState state, double quirkFactor) {
        if (state.cracking.isEmpty()) {
            return;
        }

        // Build a bounding box that covers the cracked region (plus a little headroom for standing entities).
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos pos : state.cracking.keySet()) {
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        AABB region = new AABB(minX - 0.5, minY - 0.5, minZ - 0.5, maxX + 1.5, maxY + 2.5, maxZ + 1.5);
        List<LivingEntity> entities = level.getEntitiesOfClass(LivingEntity.class, region);
        for (LivingEntity living : entities) {
            BlockPos below = living.blockPosition().below();
            BlockPos feet = living.blockPosition();
            if (state.cracking.containsKey(below) || state.cracking.containsKey(feet)) {
                DecayHelper.applyDecayEffect(level, living, quirkFactor, this.decayEffectAmplifier, 80);
            }
        }
    }

    private void spawnCrackParticles(ServerLevel level, DecayState state) {
        for (Map.Entry<BlockPos, Float> entry : state.cracking.entrySet()) {
            if (level.getRandom().nextFloat() > 0.12f) {
                continue;
            }
            BlockPos pos = entry.getKey();
            BlockState blockState = level.getBlockState(pos);
            if (blockState.isAir()) {
                continue;
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, blockState),
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    2, 0.3, 0.3, 0.3, 0.0);
            if (level.getRandom().nextFloat() < 0.4f) {
                level.sendParticles(ParticleTypes.SMOKE,
                        pos.getX() + 0.5, pos.getY() + 0.6, pos.getZ() + 0.5,
                        1, 0.2, 0.2, 0.2, 0.005);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Breaker-progress bookkeeping
    // ---------------------------------------------------------------------------------------------

    private void addCrackingBlock(DecayState state, BlockPos pos) {
        BlockPos immutable = pos.immutable();
        state.cracking.put(immutable, 0.0f);
        state.breakerIds.computeIfAbsent(immutable, p -> NEXT_BREAKER_ID.getAndIncrement());
    }

    private void updateBreakStage(ServerLevel level, DecayState state, BlockPos pos, float progress) {
        Integer breakerId = state.breakerIds.get(pos);
        if (breakerId == null) {
            return;
        }
        int stage = Math.min(9, (int) (progress * 10.0f));
        level.destroyBlockProgress(breakerId, pos, stage);
    }

    private void clearCrackingBlock(ServerLevel level, DecayState state, BlockPos pos) {
        Integer breakerId = state.breakerIds.remove(pos);
        if (breakerId != null) {
            level.destroyBlockProgress(breakerId, pos, -1);
        }
        state.cracking.remove(pos);
    }

    // ---------------------------------------------------------------------------------------------
    // Pattern helpers (ported from the legacy implementation)
    // ---------------------------------------------------------------------------------------------

    private List<BlockPos> connectedBlocks(ServerLevel level, BlockPos center, DecayState state, int intensity) {
        List<BlockPos> result = new ArrayList<>();
        switch (state.pattern) {
            case "layer" -> {
                add(result, center.north());
                add(result, center.south());
                add(result, center.east());
                add(result, center.west());
            }
            case "quarry" -> {
                add(result, center.north());
                add(result, center.south());
                add(result, center.east());
                add(result, center.west());
                add(result, center.above());
                add(result, center.below());
                add(result, center.north().east());
                add(result, center.north().west());
                add(result, center.south().east());
                add(result, center.south().west());
            }
            case "vein", "connected" -> {
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            BlockPos p = center.offset(x, y, z);
                            if (blockKey(level.getBlockState(p)).equals(state.originalBlock)) {
                                result.add(p);
                            }
                        }
                    }
                }
            }
            case "fissure" -> {
                for (int x = -1; x <= 1; x++) {
                    for (int y = -1; y <= 1; y++) {
                        for (int z = -1; z <= 1; z++) {
                            if (x == 0 && y == 0 && z == 0) continue;
                            result.add(center.offset(x, y, z));
                        }
                    }
                }
            }
            default -> { // "all" - 6-directional flood
                add(result, center.above());
                add(result, center.below());
                add(result, center.north());
                add(result, center.south());
                add(result, center.east());
                add(result, center.west());
            }
        }
        return result;
    }

    private static void add(List<BlockPos> list, BlockPos pos) {
        list.add(pos);
    }

    private int effectiveIntensity(double quirkFactor) {
        return Math.min(5, this.baseIntensity + (int) (quirkFactor * QUIRK_INTENSITY_MULTIPLIER));
    }

    private String resolvePattern(ServerPlayer player) {
        if (this.readPatternFromBody) {
            IBodyData body = BodyAttachments.get(player);
            String stored = body.getCustomString(player, DecayHelper.INSTABILITY_PART, DecayHelper.PATTERN_KEY);
            if (stored != null && !stored.isEmpty()) {
                return stored;
            }
        }
        return this.decayType;
    }

    private static String blockKey(BlockState state) {
        return state.getBlock().toString();
    }

    private boolean isDecayable(BlockState blockState, int intensity) {
        Block block = blockState.getBlock();
        if (block == Blocks.AIR || block == Blocks.BEDROCK || blockState.isAir()) {
            return false;
        }

        float destroyTime = blockState.getDestroySpeed(null, null);
        if (destroyTime < 0.0f) {
            return false; // unbreakable (-1)
        }

        if (destroyTime < 0.1f || isIntensityZero(blockState) || isIntensityOne(blockState)) {
            return intensity >= 1;
        }
        if (isIntensityTwo(blockState)) {
            return intensity >= 2;
        }
        if (isIntensityThree(blockState)) {
            return intensity >= 3;
        }
        if (isIntensityFour(blockState)) {
            return intensity >= 4;
        }
        return intensity >= 3 && destroyTime > 0 && destroyTime < 50.0f;
    }

    private boolean isIntensityZero(BlockState blockState) {
        return blockState.is(BlockTags.MINEABLE_WITH_SHOVEL) || blockState.is(BlockTags.MINEABLE_WITH_HOE);
    }

    private boolean isIntensityOne(BlockState blockState) {
        return blockState.is(BlockTags.MINEABLE_WITH_AXE);
    }

    private boolean isIntensityTwo(BlockState blockState) {
        return blockState.is(BlockTags.MINEABLE_WITH_PICKAXE)
                && !blockState.is(BlockTags.NEEDS_IRON_TOOL)
                && !blockState.is(BlockTags.NEEDS_DIAMOND_TOOL);
    }

    private boolean isIntensityThree(BlockState blockState) {
        return blockState.is(BlockTags.MINEABLE_WITH_PICKAXE)
                && (blockState.is(BlockTags.NEEDS_STONE_TOOL) || blockState.is(BlockTags.NEEDS_IRON_TOOL))
                && !blockState.is(BlockTags.NEEDS_DIAMOND_TOOL);
    }

    private boolean isIntensityFour(BlockState blockState) {
        return blockState.is(BlockTags.MINEABLE_WITH_PICKAXE)
                && (blockState.is(BlockTags.NEEDS_STONE_TOOL) || blockState.is(BlockTags.NEEDS_IRON_TOOL) || blockState.is(BlockTags.NEEDS_DIAMOND_TOOL));
    }

    private BlockPos findTargetBlock(ServerPlayer player, float range) {
        Vec3 eyePos = player.getEyePosition();
        Vec3 lookVec = player.getLookAngle();
        Vec3 endPos = eyePos.add(lookVec.scale(range));

        BlockHitResult result = player.level().clip(new ClipContext(
                eyePos, endPos,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player));

        if (result.getType() == HitResult.Type.BLOCK) {
            return result.getBlockPos();
        }
        return null;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.ENVIRONMENT_DECAY.get();
    }

    public static class Serializer extends AbilitySerializer<EnvironmentDecayAbility> {
        public MapCodec<EnvironmentDecayAbility> codec() {
            return EnvironmentDecayAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, EnvironmentDecayAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("While held, crumbles terrain in front of the user using growing block-break cracks in a chosen pattern (all, layer, quarry, fissure, vein). Blocks normally only crack (and fade back once released); a high enough quirk factor or the allow_break flag lets the decay fully consume blocks with no drops. Entities standing on decaying blocks receive the decay effect. Scales with quirk factor.")
                    .add("decay_type", TYPE_STRING, "Fallback decay pattern: all, layer, quarry, fissure, vein.")
                    .add("range", TYPE_VALUE, "How far to ray-cast for the initial target block.")
                    .add("spread_speed", TYPE_VALUE, "How quickly the crack region expands outward.")
                    .add("max_blocks", TYPE_VALUE, "Base maximum number of blocks affected (scaled up by quirk factor).")
                    .add("base_intensity", TYPE_INT, "Base hardness tier the decay can affect (1=soft .. 5=obsidian-tier).")
                    .add("crack_speed", TYPE_VALUE, "How fast each block's crack progress grows per tick.")
                    .add("fade_speed", TYPE_VALUE, "How fast cracks fade away once the ability is released.")
                    .add("instability_per_interval", TYPE_VALUE, "Instability gained per second while active.")
                    .add("break_quirk_factor", TYPE_VALUE, "Quirk factor at/above which blocks fully break instead of just cracking.")
                    .add("allow_break", TYPE_BOOLEAN, "If true, blocks always fully break (no drops) once fully cracked.")
                    .add("decay_effect_amplifier", TYPE_INT, "Base amplifier of the decay effect applied to standing entities.")
                    .add("read_pattern_from_body", TYPE_BOOLEAN, "If true, reads the active pattern from the chest body string 'decay_pattern'.")
                    .addExampleObject(new EnvironmentDecayAbility("all", new StaticValue(6.0f), new StaticValue(1.0f), new StaticValue(60.0f), 2,
                            new StaticValue(0.05f), new StaticValue(0.08f), new StaticValue(2.0f), new StaticValue(6.0f), false, 0, true,
                            AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }

    /** Per-player runtime state for an active/fading decay field. */
    private static class DecayState {
        BlockPos target;
        String originalBlock = "";
        String pattern = "all";
        int ticksSinceStart = 0;
        int currentWave = 0;
        Set<BlockPos> waveFront = new HashSet<>();
        final Map<BlockPos, Float> cracking = new HashMap<>();
        final Map<BlockPos, Integer> breakerIds = new HashMap<>();
        final Set<BlockPos> finished = new HashSet<>();
    }
}
