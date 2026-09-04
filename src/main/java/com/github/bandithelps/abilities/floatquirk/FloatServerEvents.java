package com.github.bandithelps.abilities.floatquirk;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Coast restore after movement, safety speed clamp, and knockback / explosion
 * impulse passthrough. Never cancels knockback; only marks motion dirty so the
 * client keeps the shove.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class FloatServerEvents {

    private FloatServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (FloatAbility.isActive(event.getEntity())) {
            FloatPhysics.afterMovement(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            if (FloatAbility.isActive(player)) {
                FloatPhysics.clampCurrentSpeed(player);
            }
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FloatAbility.isActive(player)) {
            player.hurtMarked = true;
            FloatPhysics.clampCurrentSpeed(player);
        }
    }

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && FloatAbility.isActive(player)) {
            player.hurtMarked = true;
            FloatPhysics.clampCurrentSpeed(player);
        }
    }

    @SubscribeEvent
    public static void onExplosion(ExplosionEvent.Detonate event) {
        for (Entity affected : event.getAffectedEntities()) {
            if (affected instanceof ServerPlayer player && FloatAbility.isActive(player)) {
                player.hurtMarked = true;
                FloatPhysics.clampCurrentSpeed(player);
            }
        }
    }
}
