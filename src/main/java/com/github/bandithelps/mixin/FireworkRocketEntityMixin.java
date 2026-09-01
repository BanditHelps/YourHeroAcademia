package com.github.bandithelps.mixin;

import com.github.bandithelps.abilities.floatquirk.FloatAbility;
import com.github.bandithelps.abilities.floatquirk.FloatFireworkEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Vanilla attached fireworks snap to the rider origin (feet). While Float is
 * on, keep the trail at hand height instead.
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketEntityMixin {

    @Shadow
    private LivingEntity attachedToEntity;

    @Shadow
    public abstract boolean isAttachedToEntity();

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/projectile/FireworkRocketEntity;setPos(DDD)V")
    )
    private void yha$floatRocketAtHand(FireworkRocketEntity rocket, double x, double y, double z) {
        LivingEntity attached = this.attachedToEntity;
        if (this.isAttachedToEntity() && attached != null && FloatAbility.isActive(attached)) {
            Vec3 hand = FloatFireworkEvents.attachedRocketPos(attached);
            rocket.setPos(hand.x, hand.y, hand.z);
            return;
        }
        rocket.setPos(x, y, z);
    }
}
