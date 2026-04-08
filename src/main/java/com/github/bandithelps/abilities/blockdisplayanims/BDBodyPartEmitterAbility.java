package com.github.bandithelps.abilities.blockdisplayanims;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.utils.blockdisplays.BlockDisplaySummoner;
import com.github.bandithelps.values.ModSettingTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
import net.threetag.palladium.util.PalladiumCodecs;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class BDBodyPartEmitterAbility extends Ability {

    public static final MapCodec<BDBodyPartEmitterAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("tick_rate", 2).forGetter((ab) -> ab.tickRate),
                    Value.CODEC.optionalFieldOf("interpolation_ticks", new StaticValue(5.0f)).forGetter((ab) -> ab.interpolationTicks),
                    Value.CODEC.optionalFieldOf("lifetime", new StaticValue(14.0f)).forGetter((ab) -> ab.lifetime),
                    PalladiumCodecs.listOrPrimitive(Codec.STRING).fieldOf("parts").forGetter((ab) -> ab.parts),
                    PalladiumCodecs.listOrPrimitive(Identifier.CODEC).optionalFieldOf("palette", Arrays.asList(Identifier.parse("minecraft:blue_stained_glass"))).forGetter((ab) -> ab.palette),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("location_offset", new Vector3f(0.0f, 0.0f, 0.0f)).forGetter((ab) -> ab.locationOffset),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("drift_offset", new Vector3f(0.0f, 0.08f, 0.0f)).forGetter((ab) -> ab.driftOffset),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("initial_scale", new Vector3f(0.22f, 0.22f, 0.22f)).forGetter((ab) -> ab.initialScale),
                    PalladiumCodecs.VECTOR_3F_CODEC.optionalFieldOf("final_scale", new Vector3f(0.0f, 0.0f, 0.0f)).forGetter((ab) -> ab.finalScale),
                    Codec.BOOL.optionalFieldOf("relative_vectors", true).forGetter((ab) -> ab.relativeVectors),
                    Codec.BOOL.optionalFieldOf("random_decay", true).forGetter((ab) -> ab.randomDecay),
                    Codec.BOOL.optionalFieldOf("random_rotation", true).forGetter((ab) -> ab.randomRotation),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, BDBodyPartEmitterAbility::new));

    public final int tickRate;
    public final Value interpolationTicks;
    public final Value lifetime;
    public final List<String> parts;
    public final List<Identifier> palette;
    public final Vector3f locationOffset;
    public final Vector3f driftOffset;
    public final Vector3f initialScale;
    public final Vector3f finalScale;
    public final boolean relativeVectors;
    public final boolean randomDecay;
    public final boolean randomRotation;

    public BDBodyPartEmitterAbility(
            int tickRate,
            Value interpolationTicks,
            Value lifetime,
            List<String> parts,
            List<Identifier> palette,
            Vector3f locationOffset,
            Vector3f driftOffset,
            Vector3f initialScale,
            Vector3f finalScale,
            boolean relativeVectors,
            boolean randomDecay,
            boolean randomRotation,
            AbilityProperties properties,
            AbilityStateManager conditions,
            List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.tickRate = tickRate;
        this.interpolationTicks = interpolationTicks;
        this.lifetime = lifetime;
        this.parts = parts;
        this.palette = palette;
        this.locationOffset = locationOffset;
        this.driftOffset = driftOffset;
        this.initialScale = initialScale;
        this.finalScale = finalScale;
        this.relativeVectors = relativeVectors;
        this.randomDecay = randomDecay;
        this.randomRotation = randomRotation;
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (!enabled || !(entity instanceof ServerPlayer player)) {
            return super.tick(entity, abilityInstance, enabled);
        }

        if (player.tickCount % this.tickRate != 0) {
            return super.tick(entity, abilityInstance, enabled);
        }

        List<BlockState> paletteBlocks = resolvePalette();
        DataContext context = DataContext.forEntity(entity);
        int interpolation = Math.max(0, this.interpolationTicks.getAsInt(context));
        int displayLifetime = Math.max(1, this.lifetime.getAsInt(context));
        float bodyYaw = player.yBodyRot;
        Vector3f rotatedLocationOffset = this.relativeVectors
                ? rotateYaw(this.locationOffset, bodyYaw)
                : new Vector3f(this.locationOffset);
        Vector3f rotatedDriftOffset = this.relativeVectors
                ? rotateYaw(this.driftOffset, bodyYaw)
                : new Vector3f(this.driftOffset);

        for (String partId : this.parts) {
            BodyPart requestedPart = BodyPart.fromId(partId);
            if (requestedPart == null) {
                continue;
            }

            BodyPart part = BodyPart.resolveForPlayer(player, requestedPart);
            Vec3 anchorPosition = getBodyPartAnchor(player, part, bodyYaw)
                    .add(rotatedLocationOffset.x, rotatedLocationOffset.y, rotatedLocationOffset.z);

            BlockDisplaySummoner.summonEmitterDisplay(
                    player.level(),
                    anchorPosition,
                    interpolation,
                    paletteBlocks,
                    this.initialScale,
                    this.finalScale,
                    rotatedDriftOffset,
                    displayLifetime,
                    this.randomDecay,
                    this.randomRotation
            );
        }

        return super.tick(entity, abilityInstance, enabled);
    }

    private List<BlockState> resolvePalette() {
        List<BlockState> paletteBlocks = new ArrayList<>();
        for (Identifier id : this.palette) {
            Block block = BuiltInRegistries.BLOCK.get(id).map(Holder::value).orElse(Blocks.AIR);
            if (block != Blocks.AIR) {
                paletteBlocks.add(block.defaultBlockState());
            }
        }

        if (paletteBlocks.isEmpty()) {
            paletteBlocks.add(Blocks.BLUE_STAINED_GLASS.defaultBlockState());
        }
        return paletteBlocks;
    }

    private static Vec3 getBodyPartAnchor(ServerPlayer player, BodyPart part, float bodyYaw) {
        double scale = player.getBbHeight() / 1.8d;
        Vec3 local = switch (part) {
            case HEAD -> new Vec3(0.0d, 1.62d * scale, 0.0d);
            case CHEST -> new Vec3(0.0d, 1.18d * scale, 0.0d);
            case LEFT_ARM -> new Vec3(0.30d * scale, 1.35d * scale, 0.0d);
            case RIGHT_ARM -> new Vec3(-0.30d * scale, 1.35d * scale, 0.0d);
            case LEFT_HAND -> new Vec3(0.1d * scale, 0.7d * scale, 0.06d * scale);
            case RIGHT_HAND -> new Vec3(-0.48d * scale, 0.7d * scale, 0.06d * scale);
            case LEFT_LEG -> new Vec3(0.16d * scale, 0.70d * scale, 0.0d);
            case RIGHT_LEG -> new Vec3(-0.16d * scale, 0.70d * scale, 0.0d);
            case LEFT_FOOT -> new Vec3(0.14d * scale, 0.12d * scale, 0.09d * scale);
            case RIGHT_FOOT -> new Vec3(-0.14d * scale, 0.12d * scale, 0.09d * scale);
            case MAIN_ARM, OFF_ARM -> new Vec3(0.0d, 1.22d * scale, 0.0d);
        };

        // Limb anchors need a small walk-cycle sway or they look detached while sprinting.
        float walkSpeed = Mth.clamp(player.walkAnimation.speed(), 0.0f, 1.0f);
        float walkPosition = player.walkAnimation.position();
        double armSwing = Math.sin(walkPosition * 0.6662f) * 0.12d * walkSpeed * scale;
        double legSwing = Math.sin((walkPosition * 0.6662f) + Math.PI) * 0.09d * walkSpeed * scale;

        local = switch (part) {
            case LEFT_ARM, LEFT_HAND -> local.add(0.0d, Math.abs(armSwing) * 0.16d, armSwing);
            case RIGHT_ARM, RIGHT_HAND -> local.add(0.0d, Math.abs(armSwing) * 0.16d, -armSwing);
            case LEFT_LEG, LEFT_FOOT -> local.add(0.0d, Math.abs(legSwing) * 0.08d, legSwing);
            case RIGHT_LEG, RIGHT_FOOT -> local.add(0.0d, Math.abs(legSwing) * 0.08d, -legSwing);
            default -> local;
        };

        if (player.isCrouching()) {
            local = local.add(0.0d, -0.2d * scale, 0.1d * scale);
        }

        Vector3f rotated = rotateYaw(new Vector3f((float) local.x, (float) local.y, (float) local.z), bodyYaw);
        return player.position().add(rotated.x, rotated.y, rotated.z);
    }

    private static Vector3f rotateYaw(Vector3f vector, float yawDegrees) {
        double yawRadians = Math.toRadians(yawDegrees);
        float cos = (float) Math.cos(yawRadians);
        float sin = (float) Math.sin(yawRadians);

        float x = vector.x * cos - vector.z * sin;
        float z = vector.x * sin + vector.z * cos;
        return new Vector3f(x, vector.y, z);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BD_BODY_PART_EMITTER.get();
    }

    public static class Serializer extends AbilitySerializer<BDBodyPartEmitterAbility> {
        public MapCodec<BDBodyPartEmitterAbility> codec() {
            return BDBodyPartEmitterAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BDBodyPartEmitterAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Attaches configurable block display emitters to body parts like hands, feet, or chest.")
                    .add("parts", ModSettingTypes.TYPE_BODY_PART, "Body parts to emit from every interval.")
                    .add("tick_rate", TYPE_INT, "How often (in ticks) to emit.")
                    .add("interpolation_ticks", TYPE_VALUE, "How long each display interpolation lasts.")
                    .add("lifetime", TYPE_VALUE, "How long each emitted display persists.")
                    .add("palette", TYPE_IDENTIFIER, "A list of block ids used for spawned block displays.")
                    .add("location_offset", TYPE_VECTOR3, "Additional offset from each body part anchor.")
                    .add("drift_offset", TYPE_VECTOR3, "The translation applied one tick after spawning.")
                    .add("initial_scale", TYPE_VECTOR3, "Initial size of each block display.")
                    .add("final_scale", TYPE_VECTOR3, "Final size after interpolation.")
                    .add("relative_vectors", TYPE_BOOLEAN, "When true, location and drift vectors rotate with player yaw.")
                    .add("random_decay", TYPE_BOOLEAN, "Whether each emitted block display gets randomized lifetime decay.")
                    .add("random_rotation", TYPE_BOOLEAN, "Whether each display starts with random right rotation.")
                    .addExampleObject(new BDBodyPartEmitterAbility(
                            2,
                            new StaticValue(5.0f),
                            new StaticValue(14.0f),
                            List.of(BodyPart.LEFT_HAND.getId(), BodyPart.RIGHT_HAND.getId()),
                            Arrays.asList(Identifier.parse("minecraft:blue_stained_glass"), Identifier.parse("minecraft:light_blue_stained_glass")),
                            new Vector3f(0.0f, 0.0f, 0.0f),
                            new Vector3f(0.0f, 0.08f, 0.0f),
                            new Vector3f(0.22f, 0.22f, 0.22f),
                            new Vector3f(0.0f, 0.0f, 0.0f),
                            true,
                            true,
                            true,
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            Collections.emptyList()
                    ));
        }
    }
}
