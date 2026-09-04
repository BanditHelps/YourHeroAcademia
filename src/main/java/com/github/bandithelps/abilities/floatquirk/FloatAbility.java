package com.github.bandithelps.abilities.floatquirk;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.utils.stamina.StaminaUtil;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.ability.AbilityUtil;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Toggleable zero-G hover. Not flight: the player keeps existing momentum,
 * can rise or sink slowly, and needs an external push for horizontal travel.
 */
public class FloatAbility extends Ability {

    public static final Identifier POWER_ID = Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "float");
    public static final String TOGGLE_KEY = "float_toggle";

    private static final Set<UUID> ACTIVE = ConcurrentHashMap.newKeySet();

    public static final MapCodec<FloatAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("max_height", new StaticValue((float) FloatPhysics.DEFAULT_MAX_HEIGHT))
                            .forGetter((ab) -> ab.maxHeight),
                    Value.CODEC.optionalFieldOf("max_speed", new StaticValue((float) FloatPhysics.DEFAULT_MAX_SPEED))
                            .forGetter((ab) -> ab.maxSpeed),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, FloatAbility::new));

    public final Value maxHeight;
    public final Value maxSpeed;

    public FloatAbility(Value maxHeight, Value maxSpeed,
                        AbilityProperties properties, AbilityStateManager conditions,
                        List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.maxHeight = maxHeight;
        this.maxSpeed = maxSpeed;
    }

    public static boolean isActive(LivingEntity entity) {
        if (entity == null) {
            return false;
        }
        if (ACTIVE.contains(entity.getUUID())) {
            return true;
        }
        AbilityInstance<?> instance = AbilityUtil.getInstance(entity, POWER_ID, TOGGLE_KEY);
        return instance != null && instance.isEnabled();
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        ACTIVE.add(entity.getUUID());
        FloatPhysics.activate(entity);
        FloatPhysics.rememberLimits(entity, resolveMaxHeight(entity, abilityInstance), resolveMaxSpeed(entity, abilityInstance));
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled) {
            ACTIVE.add(entity.getUUID());
            double height = resolveMaxHeight(entity, abilityInstance);
            double speed = resolveMaxSpeed(entity, abilityInstance);
            FloatPhysics.rememberLimits(entity, height, speed);
            FloatPhysics.tick(entity, height, speed);
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public void lastTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        ACTIVE.remove(entity.getUUID());
        FloatPhysics.deactivate(entity);
    }

    private double resolveMaxHeight(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        return Math.max(0.0d, this.maxHeight.getAsFloat(DataContext.forAbility(entity, abilityInstance)));
    }

    private double resolveMaxSpeed(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        return Math.max(0.0d, this.maxSpeed.getAsFloat(DataContext.forAbility(entity, abilityInstance)));
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.FLOAT.get();
    }

    public static class Serializer extends AbilitySerializer<FloatAbility> {
        @Override
        public MapCodec<FloatAbility> codec() {
            return FloatAbility.CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<Ability, FloatAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Toggle zero gravity. Preserves momentum, eases falls to a stop, and only self-moves up or down.")
                    .add("max_height", TYPE_VALUE, "Blocks above the solid ground underfoot. Space-bar self-ascent fades exponentially past this; fireworks and other quirks can overshoot.")
                    .add("max_speed", TYPE_VALUE, "Safety speed cap in blocks per tick. Fireworks use a separate lower cap.")
                    .addExampleObject(new FloatAbility(
                            new StaticValue(8.0f),
                            new StaticValue(8.0f),
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            Collections.emptyList()));
        }
    }
}
