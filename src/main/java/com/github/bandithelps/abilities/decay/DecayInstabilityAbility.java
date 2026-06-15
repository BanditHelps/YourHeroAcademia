package com.github.bandithelps.abilities.decay;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.capabilities.body.IBodyData;
import com.github.bandithelps.utils.decay.DecayHelper;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.List;

/**
 * Passive backbone of the Decay quirk's "control" theme. While enabled it:
 *  - Slowly recovers instability when the user has not recently used a decay ability (faster with
 *    higher control level).
 *  - Triggers devastating side effects when instability maxes out: random arm damage, an uncontrolled
 *    burst of decay around the user, and a partial instability reset. Severity is softened by control.
 */
public class DecayInstabilityAbility extends Ability {

    public static final MapCodec<DecayInstabilityAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("recovery_delay_ticks", 80).forGetter((ab) -> ab.recoveryDelayTicks),
                    ExtraCodecs.POSITIVE_INT.optionalFieldOf("recovery_per_second", 2).forGetter((ab) -> ab.recoveryPerSecond),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, DecayInstabilityAbility::new));

    public final int recoveryDelayTicks;
    public final int recoveryPerSecond;

    public DecayInstabilityAbility(int recoveryDelayTicks, int recoveryPerSecond, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.recoveryDelayTicks = recoveryDelayTicks;
        this.recoveryPerSecond = recoveryPerSecond;
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player && player.level() instanceof ServerLevel level
                && player.tickCount % 20 == 0) {

            float instability = DecayHelper.getInstability(player);
            float control = DecayHelper.getControlLevel(player);

            if (instability >= DecayHelper.MAX_INSTABILITY) {
                triggerSideEffects(player, level, control);
            } else if (!DecayHelper.usedWithin(player, this.recoveryDelayTicks) && instability > 0.0f) {
                float recovery = this.recoveryPerSecond * (1.0f + control * 0.5f);
                DecayHelper.setInstability(player, instability - recovery);
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    private void triggerSideEffects(ServerPlayer player, ServerLevel level, float control) {
        IBodyData body = DecayHelper.body(player);

        // Random arm takes a chunk of damage; control softens the blow.
        BodyPart arm = level.getRandom().nextBoolean() ? BodyPart.LEFT_ARM : BodyPart.RIGHT_ARM;
        float armDamage = Math.max(5.0f, 35.0f - control * 6.0f);
        body.damagePart(player, arm, armDamage);
        BodySyncEvents.syncNow(player);

        // Visual / audio burst of uncontrolled decay.
        level.sendParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getY() + 1, player.getZ(), 30, 0.6, 0.8, 0.6, 0.05);
        level.sendParticles(ParticleTypes.ASH, player.getX(), player.getY() + 1, player.getZ(), 25, 0.6, 0.8, 0.6, 0.03);
        level.sendParticles(ParticleTypes.SOUL, player.getX(), player.getY() + 1, player.getZ(), 12, 0.5, 0.7, 0.5, 0.02);
        level.playSound(null, player.blockPosition(), SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 1.0f, 0.6f);

        // Lash out: nearby entities get hit with decay too.
        for (LivingEntity nearby : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(4.0))) {
            if (nearby == player) continue;
            DecayHelper.applyDecayEffect(level, nearby, 1.0, 1, 80);
        }

        // Partial reset so it does not immediately retrigger.
        DecayHelper.setInstability(player, DecayHelper.MAX_INSTABILITY * 0.5f);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.DECAY_INSTABILITY.get();
    }

    public static class Serializer extends AbilitySerializer<DecayInstabilityAbility> {
        public MapCodec<DecayInstabilityAbility> codec() {
            return DecayInstabilityAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, DecayInstabilityAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Passive manager for Decay instability. Recovers instability when idle (faster with higher control) and unleashes harmful side effects when instability maxes out.")
                    .add("recovery_delay_ticks", TYPE_INT, "How long after the last decay use before instability begins recovering.")
                    .add("recovery_per_second", TYPE_INT, "Base instability recovered per second while idle.")
                    .addExampleObject(new DecayInstabilityAbility(80, 2, AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
