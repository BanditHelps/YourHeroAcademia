package com.github.bandithelps.effects;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;

public class SuffocationEffect extends MobEffect {

    public SuffocationEffect(MobEffectCategory category, int color) {
        super(category, color);
    }

    @Override
    public boolean applyEffectTick(ServerLevel serverLevel, LivingEntity mob, int amplification) {

        int currentAir = mob.getAirSupply();
        if (currentAir > 0) {
            mob.setAirSupply(currentAir - 5 - (amplification * 2));
        } else {
            var reg = mob.damageSources().damageTypes;
            var type = reg.get(DamageTypes.IN_WALL).orElse(reg.getOrThrow(DamageTypes.MAGIC));
            mob.hurtServer(serverLevel, new DamageSource(type), 1.0f * amplification + 1.0f);
        }

        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int tickCount, int amplification) {
        return true;
    }
}
