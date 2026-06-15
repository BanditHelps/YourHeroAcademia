package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.blackwhip.BlackwhipBubbleShieldAbility;
import com.github.bandithelps.utils.stamina.StaminaUtil;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Applies the Blackwhip bubble shield's damage absorption: while a player has an active shield, a
 * fraction of incoming (optionally frontal-only) damage is negated, the player is knocked back from the
 * blow, and stamina is spent proportional to the damage prevented.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class BlackwhipShieldEvents {

    private BlackwhipShieldEvents() {
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BlackwhipBubbleShieldAbility.ShieldSettings settings = BlackwhipBubbleShieldAbility.getActive(player.getUUID());
        if (settings == null || settings.absorb() <= 0.0f) {
            return;
        }

        Entity attacker = event.getSource().getDirectEntity();
        if (attacker == null) {
            attacker = event.getSource().getEntity();
        }

        if (settings.frontalOnly() && attacker != null) {
            Vec3 toAttacker = attacker.position().subtract(player.position());
            if (toAttacker.lengthSqr() > 1.0e-4) {
                if (player.getLookAngle().normalize().dot(toAttacker.normalize()) <= 0.0) {
                    return; // attack came from behind the dome
                }
            }
        }

        float amount = event.getAmount();
        float blocked = amount * settings.absorb();
        if (blocked <= 0.0f) {
            return;
        }
        event.setAmount(amount - blocked);

        int staminaCost = Mth.ceil(blocked * settings.staminaPerDamage());
        if (staminaCost > 0) {
            StaminaUtil.useStamina(player, staminaCost);
        }

        if (settings.knockback() > 0.0f) {
            Vec3 back = attacker != null
                    ? player.position().subtract(attacker.position())
                    : player.getLookAngle().scale(-1.0);
            if (back.lengthSqr() > 1.0e-4) {
                back = back.normalize();
                player.push(back.x * settings.knockback(), 0.15, back.z * settings.knockback());
                player.hurtMarked = true;
            }
        }

        if (player.level() instanceof ServerLevel level) {
            level.playSound(null, player.blockPosition(), SoundEvents.SHIELD_BLOCK.value(), SoundSource.PLAYERS, 0.5f, 0.6f);
        }
    }
}
