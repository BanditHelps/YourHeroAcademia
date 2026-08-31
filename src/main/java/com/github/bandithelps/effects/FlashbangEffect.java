package com.github.bandithelps.effects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/**
 * Flashbang aftermath. Players are blinded via client overlay/fog; mobs are stunned in place.
 */
public class FlashbangEffect extends MobEffect {
    public FlashbangEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
            mob.setAggressive(false);
            Vec3 velocity = mob.getDeltaMovement();
            mob.setDeltaMovement(0.0, velocity.y, 0.0);
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
