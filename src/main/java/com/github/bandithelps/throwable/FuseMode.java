package com.github.bandithelps.throwable;

/**
 * When a thrown weapon starts counting down to detonation.
 */
public enum FuseMode {
    /** Fuse starts as soon as the projectile is spawned. */
    FROM_THROW,
    /** Fuse starts on the first block or entity hit. */
    FROM_IMPACT
}
