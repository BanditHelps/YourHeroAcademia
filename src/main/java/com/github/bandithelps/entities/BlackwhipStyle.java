package com.github.bandithelps.entities;

/**
 * Visual style of a {@link BlackwhipEntity}. The renderer switches on this to build the
 * appropriate polyline(s) for the strand(s).
 */
public enum BlackwhipStyle {
    /** Hand/hip to a living target entity. Wraps the target with rings. */
    TETHER,
    /** Hand/hip to a fixed world point (grapple anchor that is not being swung on). */
    ANCHOR_ROPE,
    /** Hand/hip to a fixed world point used for Spider-Man swinging. */
    SWING_ROPE,
    /** Rings wrapped around a target entity only (no rope). */
    WRAP,
    /** Procedural tendrils orbiting the owner's back. */
    AURA,
    /** Procedural petals forming a forward shield bubble. */
    BUBBLE,
    /** Hand/hip to a carried block-stack entity. */
    BLOCK_GRAB,
    /** A short sweeping lash arc in front of the owner. */
    LASH;

    private static final BlackwhipStyle[] VALUES = values();

    public static BlackwhipStyle byOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) {
            return TETHER;
        }
        return VALUES[ordinal];
    }
}
