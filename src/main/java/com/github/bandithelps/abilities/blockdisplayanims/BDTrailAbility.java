package com.github.bandithelps.abilities.blockdisplayanims;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.utils.blockdisplays.BlockDisplaySummoner;
import com.github.bandithelps.utils.blockdisplays.BlockDisplayVisualOptions;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.*;
import net.threetag.palladium.power.energybar.EnergyBarUsage;
import net.threetag.palladium.util.PalladiumCodecs;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BDTrailAbility extends Ability {
    public static final MapCodec<BDTrailAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("interval", new StaticValue(2)).forGetter((ab) -> ab.interval),
                    Value.CODEC.optionalFieldOf("interpolation_ticks", new StaticValue(10)).forGetter((ab) -> ab.interpolationTicks),
                    Value.CODEC.optionalFieldOf("lifetime", new StaticValue(40)).forGetter((ab) -> ab.lifetime),
                    PalladiumCodecs.listOrPrimitive(Identifier.CODEC).optionalFieldOf("palette", Arrays.asList(Identifier.parse("minecraft:diamond_block"))).forGetter((ab) -> ab.palette),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("location_offset", new Vector3f(0.0f, 0.0f, 0.0f)).forGetter((ab) -> ab.locationOffset),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("rotation_offset", new Vector3f(0.0f, 0.0f, 0.0f)).forGetter((ab) -> ab.rotationOffset),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("initial_scale", new Vector3f(0.3f, 0.3f, 0.3f)).forGetter((ab) -> ab.initialScale),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("final_scale", new Vector3f(0.6f, 0.6f, 0.6f)).forGetter((ab) -> ab.finalScale),
                    Codec.BOOL.optionalFieldOf("random_decay", true).forGetter((ab) -> ab.randomDecay),
                    Codec.BOOL.optionalFieldOf("random_rotation", true).forGetter((ab) -> ab.randomRotation),
                    Codec.BOOL.optionalFieldOf("relative", false).forGetter((ab) -> ab.relative),
                    IdleRotationConfig.CODEC.codec().optionalFieldOf("idle_rotation", IdleRotationConfig.DEFAULT).forGetter((ab) -> ab.idleRotationConfig),
                    BlockDisplayVisualOptions.CODEC.optionalFieldOf("visual_options", BlockDisplayVisualOptions.DEFAULT).forGetter((ab) -> ab.visualOptions),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BDTrailAbility::new));

    public final Value interval;
    public final Value interpolationTicks;
    public final Value lifetime;
    public final List<Identifier> palette;
    public final Vector3f locationOffset;
    public final Vector3f rotationOffset;
    public final Vector3f initialScale;
    public final Vector3f finalScale;
    public final boolean randomDecay;
    public final boolean randomRotation;
    public final boolean relative;
    public final IdleRotationConfig idleRotationConfig;
    public final BlockDisplayVisualOptions visualOptions;

    public BDTrailAbility(Value interval, Value interpolationTicks, Value lifetime, List<Identifier> palette, Vector3f locationOffset, Vector3f rotationOffset, Vector3f initialScale, Vector3f finalScale, boolean randomDecay, boolean randomRotation, boolean relative, IdleRotationConfig idleRotationConfig, BlockDisplayVisualOptions visualOptions, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.interval = interval;
        this.interpolationTicks = interpolationTicks;
        this.lifetime = lifetime;
        this.palette = palette;
        this.locationOffset = locationOffset;
        this.rotationOffset = rotationOffset;
        this.initialScale = initialScale;
        this.finalScale = finalScale;
        this.randomDecay = randomDecay;
        this.randomRotation = randomRotation;
        this.relative = relative;
        this.idleRotationConfig = idleRotationConfig;
        this.visualOptions = visualOptions;
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (!enabled || !(entity instanceof ServerPlayer player)) {
            return super.tick(entity, abilityInstance, enabled);
        }

        int interval = Math.max(1, this.interval.getAsInt(DataContext.forEntity(entity)));
        if (player.tickCount % interval != 0) {
            return super.tick(entity, abilityInstance, enabled);
        }

        int interpolationTicks = Math.max(0, this.interpolationTicks.getAsInt(DataContext.forEntity(entity)));
        int lifetime = Math.max(1, this.lifetime.getAsInt(DataContext.forEntity(entity)));

        List<BlockState> paletteBlocks = new ArrayList<>();
        for (Identifier id : this.palette) {
            Block block = BuiltInRegistries.BLOCK.get(id).map(Holder::value).orElse(Blocks.AIR);
            paletteBlocks.add(block.defaultBlockState());
        }

        Vec3 spawnPosition = calculateSpawnPosition(player);

        BlockDisplaySummoner.summonTrailDisplay(
                player.level(),
                spawnPosition,
                this.locationOffset,
                this.rotationOffset,
                this.initialScale,
                this.finalScale,
                paletteBlocks,
                interpolationTicks,
                lifetime,
                this.randomDecay,
                this.randomRotation,
                this.relative,
                this.idleRotationConfig,
                this.visualOptions
        );

        return super.tick(entity, abilityInstance, enabled);
    }

    private Vec3 calculateSpawnPosition(ServerPlayer player) {
        if (this.relative) {
            Vec3 forward = player.getLookAngle().normalize();
            Vec3 up = new Vec3(0, 1, 0);
            Vec3 right = forward.cross(up).normalize();
            up = right.cross(forward).normalize();

            Vec3 offset = right.scale(this.locationOffset.x)
                    .add(up.scale(this.locationOffset.y))
                    .add(forward.scale(this.locationOffset.z));

            return player.position().add(offset);
        } else {
            return new Vec3(
                    player.getX() + this.locationOffset.x(),
                    player.getY() + this.locationOffset.y(),
                    player.getZ() + this.locationOffset.z()
            );
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BD_TRAIL.get();
    }

    public static class Serializer extends AbilitySerializer<BDTrailAbility> {
        public MapCodec<BDTrailAbility> codec() { return BDTrailAbility.CODEC; }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BDTrailAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Spawns a trail of block displays at the player's position while enabled")
                    .add("interval", TYPE_VALUE, "Ticks between each block display spawn")
                    .add("interpolation_ticks", TYPE_VALUE, "How many ticks the spawn animation takes")
                    .add("lifetime", TYPE_VALUE, "How long each block display persists")
                    .add("palette", TYPE_IDENTIFIER, "A list of block ids for the displays")
                    .add("location_offset", TYPE_VECTOR3, "Position offset from the player")
                    .add("rotation_offset", TYPE_VECTOR3, "Rotation in degrees for each display")
                    .add("initial_scale", TYPE_VECTOR3, "Initial size when spawned")
                    .add("final_scale", TYPE_VECTOR3, "Size after interpolation completes")
                    .add("random_decay", TYPE_BOOLEAN, "Whether displays disappear at random times")
                    .add("random_rotation", TYPE_BOOLEAN, "Whether displays start with random rotation")
                    .add("relative", TYPE_BOOLEAN, "Use relative coordinates (caret notation)")
                    .add("idle_rotation", TYPE_VALUE, "Idle rotation configuration object")
                    .addExampleObject(new BDTrailAbility(
                            new StaticValue(2),
                            new StaticValue(10),
                            new StaticValue(40),
                            Arrays.asList(Identifier.parse("minecraft:diamond_block")),
                            new Vector3f(0, 0, 0),
                            new Vector3f(0, 0, 0),
                            new Vector3f(0.3f, 0.3f, 0.3f),
                            new Vector3f(0.6f, 0.6f, 0.6f),
                            true,
                            true,
                            false,
                            IdleRotationConfig.DEFAULT,
                            BlockDisplayVisualOptions.DEFAULT,
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            Collections.emptyList()
                    ));
        }
    }

    public static class IdleRotationConfig {
        public static final MapCodec<IdleRotationConfig> CODEC = RecordCodecBuilder.mapCodec((instance) ->
                instance.group(
                        Codec.INT.optionalFieldOf("interval_min", 14).forGetter((cfg) -> cfg.intervalMin),
                        Codec.INT.optionalFieldOf("interval_max", 28).forGetter((cfg) -> cfg.intervalMax),
                        Codec.FLOAT.optionalFieldOf("degrees_min", 0.8f).forGetter((cfg) -> cfg.degreesMin),
                        Codec.FLOAT.optionalFieldOf("degrees_max", 1.9f).forGetter((cfg) -> cfg.degreesMax)
                ).apply(instance, IdleRotationConfig::new));

        public static final IdleRotationConfig DEFAULT = new IdleRotationConfig(14, 28, 0.8f, 1.9f);

        public final int intervalMin;
        public final int intervalMax;
        public final float degreesMin;
        public final float degreesMax;

        public IdleRotationConfig(int intervalMin, int intervalMax, float degreesMin, float degreesMax) {
            this.intervalMin = intervalMin;
            this.intervalMax = intervalMax;
            this.degreesMin = degreesMin;
            this.degreesMax = degreesMax;
        }
    }
}