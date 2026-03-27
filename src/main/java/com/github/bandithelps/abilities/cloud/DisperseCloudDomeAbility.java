package com.github.bandithelps.abilities.cloud;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.cloud.CloudVolumeManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
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

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class DisperseCloudDomeAbility extends Ability {
    public static final MapCodec<DisperseCloudDomeAbility> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    DisperseShape.CODEC.optionalFieldOf("shape", DisperseShape.DOME).forGetter(ab -> ab.shape),
                    Value.CODEC.optionalFieldOf("radius", new StaticValue(8.0F)).forGetter(ab -> ab.radius),
                    Value.CODEC.optionalFieldOf("strength", new StaticValue(1.0F)).forGetter(ab -> ab.strength),
                    Value.CODEC.optionalFieldOf("front_width", new StaticValue(8.0F)).forGetter(ab -> ab.frontWidth),
                    Value.CODEC.optionalFieldOf("front_height", new StaticValue(6.0F)).forGetter(ab -> ab.frontHeight),
                    Value.CODEC.optionalFieldOf("front_depth", new StaticValue(10.0F)).forGetter(ab -> ab.frontDepth),
                    Value.CODEC.optionalFieldOf("cone_angle_degrees", new StaticValue(35.0F)).forGetter(ab -> ab.coneAngleDegrees),
                    Value.CODEC.optionalFieldOf("forward_offset", new StaticValue(0.75F)).forGetter(ab -> ab.forwardOffset),
                    Codec.BOOL.optionalFieldOf("continuous", false).forGetter(ab -> ab.continuous),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("tick_rate", 1).forGetter(ab -> ab.tickRate),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, DisperseCloudDomeAbility::new)
    );

    private final DisperseShape shape;
    private final Value radius;
    private final Value strength;
    private final Value frontWidth;
    private final Value frontHeight;
    private final Value frontDepth;
    private final Value coneAngleDegrees;
    private final Value forwardOffset;
    private final boolean continuous;
    private final int tickRate;

    public DisperseCloudDomeAbility(
            DisperseShape shape,
            Value radius,
            Value strength,
            Value frontWidth,
            Value frontHeight,
            Value frontDepth,
            Value coneAngleDegrees,
            Value forwardOffset,
            boolean continuous,
            int tickRate,
            AbilityProperties properties,
            AbilityStateManager conditions,
            List<EnergyBarUsage> energyBarUsages
    ) {
        super(properties, conditions, energyBarUsages);
        this.shape = shape;
        this.radius = radius;
        this.strength = strength;
        this.frontWidth = frontWidth;
        this.frontHeight = frontHeight;
        this.frontDepth = frontDepth;
        this.coneAngleDegrees = coneAngleDegrees;
        this.forwardOffset = forwardOffset;
        this.continuous = continuous;
        this.tickRate = Math.max(1, tickRate);
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        applyDispersal(entity);
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && this.continuous && entity.tickCount % this.tickRate == 0) {
            applyDispersal(entity);
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    private void applyDispersal(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        DataContext context = DataContext.forEntity(entity);
        float radiusValue = Math.max(0.5F, this.radius.getAsFloat(context));
        float strengthValue = Math.max(0.05F, this.strength.getAsFloat(context));
        Vec3 forward = entity.getLookAngle();
        if (forward.lengthSqr() < 0.000001D) {
            forward = new Vec3(0.0D, 0.0D, 1.0D);
        } else {
            forward = forward.normalize();
        }
        double offset = Math.max(0.0D, this.forwardOffset.getAsFloat(context));
        Vec3 origin = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D).add(forward.scale(offset));
        CloudVolumeManager manager = CloudVolumeManager.forLevel(level);

        switch (this.shape) {
            case DOME -> manager.disperseDome(origin, radiusValue, strengthValue);
            case FRONT -> {
                double width = Math.max(0.5D, this.frontWidth.getAsFloat(context));
                double height = Math.max(0.5D, this.frontHeight.getAsFloat(context));
                double depth = Math.max(0.5D, this.frontDepth.getAsFloat(context));
                manager.disperseFront(origin, forward, width, height, depth, strengthValue);
            }
            case CONE -> {
                double coneAngle = Math.max(5.0D, this.coneAngleDegrees.getAsFloat(context));
                manager.disperseCone(origin, forward, radiusValue, coneAngle, strengthValue);
            }
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.DISPERSE_CLOUD_DOME.get();
    }

    public static class Serializer extends AbilitySerializer<DisperseCloudDomeAbility> {
        @Override
        public MapCodec<DisperseCloudDomeAbility> codec() {
            return DisperseCloudDomeAbility.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<Ability, DisperseCloudDomeAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Disperses cloud density in a dome around the entity.")
                    .add("shape", TYPE_STRING, "Dispersal shape: dome, front, or cone.")
                    .add("radius", TYPE_VALUE, "Dome radius in blocks.")
                    .add("strength", TYPE_VALUE, "How much cloud density to remove.")
                    .add("front_width", TYPE_VALUE, "Width for front rectangle dispersal.")
                    .add("front_height", TYPE_VALUE, "Height for front rectangle dispersal.")
                    .add("front_depth", TYPE_VALUE, "Depth for front rectangle dispersal.")
                    .add("cone_angle_degrees", TYPE_VALUE, "Half-angle of cone dispersal.")
                    .add("forward_offset", TYPE_VALUE, "How far in front of entity dispersal starts.")
                    .add("continuous", TYPE_BOOLEAN, "If true, dispersal repeats while enabled.")
                    .add("tick_rate", TYPE_INT, "Tick interval for continuous dispersal.")
                    .addExampleObject(new DisperseCloudDomeAbility(
                            DisperseShape.DOME,
                            new StaticValue(8.0F),
                            new StaticValue(1.0F),
                            new StaticValue(8.0F),
                            new StaticValue(6.0F),
                            new StaticValue(10.0F),
                            new StaticValue(35.0F),
                            new StaticValue(0.75F),
                            false,
                            1,
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            Collections.emptyList()
                    ));
        }
    }

    private enum DisperseShape {
        DOME("dome"),
        FRONT("front"),
        CONE("cone");

        private static final Codec<DisperseShape> CODEC = Codec.STRING.xmap(DisperseShape::fromId, DisperseShape::id);
        private final String id;

        DisperseShape(String id) {
            this.id = id;
        }

        private String id() {
            return this.id;
        }

        private static DisperseShape fromId(String id) {
            String normalized = id == null ? "" : id.toLowerCase(Locale.ROOT);
            for (DisperseShape shape : values()) {
                if (shape.id.equals(normalized)) {
                    return shape;
                }
            }
            return DOME;
        }
    }
}
