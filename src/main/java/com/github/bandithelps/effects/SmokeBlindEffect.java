package com.github.bandithelps.effects;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;

public class SmokeBlindEffect extends MobEffect {
    private static final double FOLLOW_RANGE_REDUCTION = -0.5D;
    private static final Identifier FOLLOW_RANGE_MODIFIER_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "effect.smoke_blind.follow_range");

    public SmokeBlindEffect(MobEffectCategory category, int color) {
        super(category, color);
        this.addAttributeModifier(
                Attributes.FOLLOW_RANGE,
                FOLLOW_RANGE_MODIFIER_ID,
                FOLLOW_RANGE_REDUCTION,
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL
        );
    }

    @Override
    public boolean applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        if (!(entity instanceof Mob mob)) {
            return true;
        }

        LivingEntity target = mob.getTarget();
        if (!(target instanceof Player)) {
            return true;
        }

        double followRange = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
        if (followRange <= 0.0D) {
            return true;
        }

        if (mob.distanceToSqr(target) > followRange * followRange) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
        return true;
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }
}
