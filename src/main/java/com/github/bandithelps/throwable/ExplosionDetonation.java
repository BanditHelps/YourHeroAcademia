package com.github.bandithelps.throwable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class ExplosionDetonation implements ThrowableDetonation {
    public static final ExplosionDetonation INSTANCE = new ExplosionDetonation();

    private ExplosionDetonation() {
    }

    @Override
    public void detonate(ThrownWeaponEntity projectile, ServerLevel level, ThrowableWeaponSpec spec) {
        float radius = spec.scaledExplosionRadius();
        if (radius <= 0.0f) {
            projectile.discard();
            return;
        }

        boolean breakBlocks = spec.shouldBreakBlocks(level);
        Level.ExplosionInteraction interaction = breakBlocks
                ? Level.ExplosionInteraction.TNT
                : Level.ExplosionInteraction.NONE;
        ThrowableExplosionDamageCalculator calculator = new ThrowableExplosionDamageCalculator(
                breakBlocks,
                spec.scaledExplosionDamage() != 0.0f,
                spec.explosionKnockback(),
                spec.scaledExplosionDamage()
        );
        level.explode(
                projectile,
                null,
                calculator,
                projectile.getX(),
                projectile.getY() + 0.45,
                projectile.getZ(),
                radius,
                false,
                interaction
        );
        projectile.discard();
    }
}
