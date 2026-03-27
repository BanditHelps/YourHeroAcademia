package com.github.bandithelps.abilities.cloud;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.cloud.CloudMode;
import com.github.bandithelps.cloud.CloudSimConfig;
import com.github.bandithelps.cloud.CloudVolume;
import com.github.bandithelps.cloud.CloudVolumeManager;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
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

import java.util.Collections;
import java.util.List;

public final class CreateSmokeCloudAbility extends Ability {
    public static final MapCodec<CreateSmokeCloudAbility> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Value.CODEC.optionalFieldOf("radius", new StaticValue(6.0F)).forGetter(ab -> ab.radius),
                    Value.CODEC.optionalFieldOf("density", new StaticValue(0.9F)).forGetter(ab -> ab.density),
                    Codec.FLOAT.optionalFieldOf("cell_size", 1.0F).forGetter(ab -> ab.cellSize),
                    Codec.STRING.optionalFieldOf("mode", CloudMode.DIFFUSE.getId()).forGetter(ab -> ab.mode),
                    Codec.INT.optionalFieldOf("lifetime_ticks", CloudSimConfig.defaultLifetimeTicks()).forGetter(ab -> ab.lifetimeTicks),
                    Value.CODEC.optionalFieldOf("projection_range", new StaticValue(16.0F)).forGetter(ab -> ab.projectionRange),
                    Codec.FLOAT.optionalFieldOf("projection_step", 0.75F).forGetter(ab -> ab.projectionStep),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, CreateSmokeCloudAbility::new)
    );

    private final Value radius;
    private final Value density;
    private final float cellSize;
    private final String mode;
    private final int lifetimeTicks;
    private final Value projectionRange;
    private final float projectionStep;

    public CreateSmokeCloudAbility(
            Value radius,
            Value density,
            float cellSize,
            String mode,
            int lifetimeTicks,
            Value projectionRange,
            float projectionStep,
            AbilityProperties properties,
            AbilityStateManager conditions,
            List<EnergyBarUsage> energyBarUsages
    ) {
        super(properties, conditions, energyBarUsages);
        this.radius = radius;
        this.density = density;
        this.cellSize = cellSize;
        this.mode = mode;
        this.lifetimeTicks = lifetimeTicks;
        this.projectionRange = projectionRange;
        this.projectionStep = projectionStep;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        float radiusValue = Math.max(0.5F, this.radius.getAsFloat(DataContext.forEntity(entity)));
        float densityValue = Math.max(0.05F, this.density.getAsFloat(DataContext.forEntity(entity)));
        Vec3 center = entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
        Vec3 eyePos = entity.getEyePosition();
        Vec3 look = entity.getLookAngle().normalize();
        float projectionRangeValue = Math.max(2.0F, this.projectionRange.getAsFloat(DataContext.forEntity(entity)));
        float projectionStepValue = Math.max(0.2F, this.projectionStep);

        CloudVolumeManager manager = CloudVolumeManager.forLevel(level);
        CloudMode cloudMode = parseMode(this.mode);
        CloudVolume volume = manager.createVolume(
                center,
                Math.max(0.5F, this.cellSize),
                cloudMode,
                Math.max(20, this.lifetimeTicks)
        );

        if (cloudMode == CloudMode.FLOOD_FILL) {
            Vec3 targetPoint = findProjectionHit(level, entity, eyePos, look, projectionRangeValue);
            spawnProjectionTrail(volume, eyePos, targetPoint, projectionStepValue, densityValue);
            volume.queueFloodSeed(targetPoint.subtract(look.scale(Math.max(0.4D, volume.cellSize() * 0.6D))));
            volume.addSphereDensity(targetPoint, Math.max(1.5D, radiusValue * 0.5D), Math.max(0.3F, densityValue * 0.6F));
        } else {
            volume.addSphereDensity(center, radiusValue, densityValue);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.CREATE_SMOKE_CLOUD.get();
    }

    private static CloudMode parseMode(String modeId) {
        for (CloudMode mode : CloudMode.values()) {
            if (mode.getId().equalsIgnoreCase(modeId)) {
                return mode;
            }
        }
        return CloudMode.DIFFUSE;
    }

    public static class Serializer extends AbilitySerializer<CreateSmokeCloudAbility> {
        @Override
        public MapCodec<CreateSmokeCloudAbility> codec() {
            return CreateSmokeCloudAbility.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<Ability, CreateSmokeCloudAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Creates a server-authoritative smoke cloud volume that can be dispersed by attacks.")
                    .add("radius", TYPE_VALUE, "Radius of the cloud in blocks.")
                    .add("density", TYPE_VALUE, "Initial cloud density.")
                    .add("cell_size", TYPE_FLOAT, "Voxel size used for cloud simulation.")
                    .add("mode", TYPE_STRING, "Cloud simulation mode: diffuse or flood_fill.")
                    .add("lifetime_ticks", TYPE_FLOAT, "Cloud lifetime in ticks before expiry.")
                    .add("projection_range", TYPE_VALUE, "For flood_fill mode: how far smoke projects before filling.")
                    .add("projection_step", TYPE_FLOAT, "For flood_fill mode: spacing of the projected smoke trail.")
                    .addExampleObject(new CreateSmokeCloudAbility(
                            new StaticValue(6.0F),
                            new StaticValue(0.8F),
                            1.0F,
                            CloudMode.DIFFUSE.getId(),
                            20 * 15,
                            new StaticValue(16.0F),
                            0.75F,
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            Collections.emptyList()
                    ));
        }
    }

    private static Vec3 findProjectionHit(ServerLevel level, LivingEntity caster, Vec3 start, Vec3 look, float range) {
        Vec3 end = start.add(look.scale(range));
        BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return end;
    }

    private static void spawnProjectionTrail(CloudVolume volume, Vec3 start, Vec3 end, float step, float density) {
        Vec3 delta = end.subtract(start);
        double length = delta.length();
        if (length < 0.0001D) {
            return;
        }

        Vec3 dir = delta.scale(1.0D / length);
        double t = 0.0D;
        while (t <= length) {
            Vec3 point = start.add(dir.scale(t));
            volume.addSphereDensity(point, Math.max(0.45D, volume.cellSize() * 0.6D), Math.max(0.08F, density * 0.2F));
            t += step;
        }
    }
}
