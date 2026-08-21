package com.github.bandithelps.utils.decay;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.capabilities.body.IBodyData;
import com.github.bandithelps.effects.ModEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central helper for the Decay quirk. Manages the "instability" resource that builds up as the
 * power is used, and provides a shared way to apply the decay effect to entities scaled by the
 * user's quirk factor.
 *
 * Instability is stored on the body system (chest, key {@code decay_instability}) so the
 * existing body-bar display + sync pipeline can render it. Control upgrades store a level in
 * the chest under {@code decay_control}, which dampens instability gain and softens side effects.
 */
public final class DecayHelper {

    public static final BodyPart INSTABILITY_PART = BodyPart.CHEST;
    public static final String INSTABILITY_KEY = "decay_instability";
    public static final String CONTROL_KEY = "decay_control";
    public static final String PATTERN_KEY = "decay_pattern";

    public static final float MAX_INSTABILITY = 100.0f;

    // Tracks the last server tick each player actively used a decay ability. Used to gate recovery.
    private static final Map<UUID, Long> LAST_USE_TICK = new ConcurrentHashMap<>();

    private DecayHelper() {
    }

    public static IBodyData body(ServerPlayer player) {
        return BodyAttachments.get(player);
    }

    public static float getControlLevel(ServerPlayer player) {
        return body(player).getCustomFloat(player, INSTABILITY_PART, CONTROL_KEY, 0.0f);
    }

    public static float getInstability(ServerPlayer player) {
        return body(player).getCustomFloat(player, INSTABILITY_PART, INSTABILITY_KEY, 0.0f);
    }

    public static void setInstability(ServerPlayer player, float value) {
        float clamped = Math.max(0.0f, Math.min(value, MAX_INSTABILITY));
        body(player).setCustomFloat(player, INSTABILITY_PART, INSTABILITY_KEY, clamped);
        BodySyncEvents.syncNow(player);
    }

    /**
     * Adds instability, scaled down by the player's control level. Control 0 = full gain,
     * each control level reduces gain by 20% (down to a 25% floor). Records the use tick so
     * passive recovery knows the player is actively decaying.
     */
    public static void addInstability(ServerPlayer player, float baseAmount) {
        if (baseAmount <= 0.0f) {
            markUsed(player);
            return;
        }

        float control = getControlLevel(player);
        float gainMultiplier = Math.max(0.25f, 1.0f - (control * 0.2f));
        float current = getInstability(player);
        setInstability(player, current + (baseAmount * gainMultiplier));
        markUsed(player);
    }

    public static void markUsed(ServerPlayer player) {
        LAST_USE_TICK.put(player.getUUID(), player.level().getGameTime());
    }

    public static boolean usedWithin(ServerPlayer player, long ticks) {
        Long last = LAST_USE_TICK.get(player.getUUID());
        if (last == null) {
            return false;
        }
        return (player.level().getGameTime() - last) <= ticks;
    }

    public static void clear(ServerPlayer player) {
        LAST_USE_TICK.remove(player.getUUID());
    }

    /**
     * Applies the decay effect to a target, with amplifier scaled by the attacker's quirk factor.
     *
     * @param baseAmplifier the floor amplifier before quirk scaling
     * @param durationTicks  how long the effect lasts
     */
    public static void applyDecayEffect(ServerLevel level, LivingEntity target, double quirkFactor, int baseAmplifier, int durationTicks) {
        int quirkBonus = (int) Math.floor(quirkFactor);
        int amplifier = Math.min(baseAmplifier + quirkBonus, 9);

        target.addEffect(new MobEffectInstance(
                ModEffects.DECAY,
                durationTicks,
                amplifier,
                false,
                true
        ));

        level.sendParticles(ParticleTypes.LARGE_SMOKE,
                target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
                3, 0.3, 0.5, 0.3, 0.02);
    }
}
