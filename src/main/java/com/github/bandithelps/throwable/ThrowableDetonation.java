package com.github.bandithelps.throwable;

import net.minecraft.server.level.ServerLevel;

@FunctionalInterface
public interface ThrowableDetonation {
    ThrowableDetonation NONE = (projectile, level, spec) -> projectile.discard();

    void detonate(ThrownWeaponEntity projectile, ServerLevel level, ThrowableWeaponSpec spec);
}
