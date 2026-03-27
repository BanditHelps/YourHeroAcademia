package com.github.bandithelps.abilities.cloud;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.cloud.CloudVolumeManager;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
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

public final class DisperseCloudDomeAbility extends Ability {
    public static final MapCodec<DisperseCloudDomeAbility> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Value.CODEC.optionalFieldOf("radius", new StaticValue(8.0F)).forGetter(ab -> ab.radius),
                    Value.CODEC.optionalFieldOf("strength", new StaticValue(1.0F)).forGetter(ab -> ab.strength),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, DisperseCloudDomeAbility::new)
    );

    private final Value radius;
    private final Value strength;

    public DisperseCloudDomeAbility(
            Value radius,
            Value strength,
            AbilityProperties properties,
            AbilityStateManager conditions,
            List<EnergyBarUsage> energyBarUsages
    ) {
        super(properties, conditions, energyBarUsages);
        this.radius = radius;
        this.strength = strength;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        float radiusValue = Math.max(0.5F, this.radius.getAsFloat(DataContext.forEntity(entity)));
        float strengthValue = Math.max(0.05F, this.strength.getAsFloat(DataContext.forEntity(entity)));
        Vec3 center = entity.position();
        CloudVolumeManager.forLevel(level).disperseDome(center, radiusValue, strengthValue);
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
                    .add("radius", TYPE_VALUE, "Dome radius in blocks.")
                    .add("strength", TYPE_VALUE, "How much cloud density to remove.")
                    .addExampleObject(new DisperseCloudDomeAbility(
                            new StaticValue(8.0F),
                            new StaticValue(1.0F),
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            Collections.emptyList()
                    ));
        }
    }
}
