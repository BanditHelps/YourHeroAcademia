package com.github.bandithelps.entities;

/**
 * Describes where on the owner a whip strand originates. Resolved client-side in the renderer
 * so the attach point stays smooth with the player's body animation.
 */
public enum BlackwhipAnchor {
    /** Main-arm side hip (default firing point). */
    HAND,
    /** Right hip. */
    RIGHT_HAND,
    /** Left hip. */
    LEFT_HAND,
    /** Right side, raised and pushed forward (used by Restrain). */
    RIGHT_HIGH,
    /** Upper back (used by procedural aura/bubble styles). */
    BACK;

    private static final BlackwhipAnchor[] VALUES = values();

    public static BlackwhipAnchor byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) {
            return HAND;
        }
        return VALUES[ordinal];
    }
}
