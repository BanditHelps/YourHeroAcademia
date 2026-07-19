package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.entities.BlackwhipEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Resolves a player's customized Blackwhip colors from the body-data string system. Colors are stored
 * as {@code RRGGBB} (or {@code AARRGGBB}) hex strings on the chest under
 * {@link #CORE_KEY} / {@link #OUTER_KEY} / {@link #GLOW_KEY} and are set via the
 * {@code /yha blackwhip color} command.
 */
public final class BlackwhipColors {

    public static final BodyPart PART = BodyPart.CHEST;
    public static final String CORE_KEY = "blackwhip_color_core";
    public static final String OUTER_KEY = "blackwhip_color_outer";
    public static final String GLOW_KEY = "blackwhip_color_glow";

    /** Default canon colors: near-black core, teal mid ribbon, translucent teal glow halo. */
    public static final int DEFAULT_CORE = BlackwhipEntity.DEFAULT_CORE;
    public static final int DEFAULT_OUTER = BlackwhipEntity.DEFAULT_OUTER;
    public static final int DEFAULT_GLOW = BlackwhipEntity.DEFAULT_GLOW;

    private static final int CORE_DEFAULT_ALPHA = 0xFF;
    private static final int OUTER_DEFAULT_ALPHA = 0xE0;
    private static final int GLOW_DEFAULT_ALPHA = 0xB3;

    private BlackwhipColors() {
    }

    public static int getCore(Player player) {
        return resolve(readString(player, CORE_KEY), DEFAULT_CORE, CORE_DEFAULT_ALPHA);
    }

    public static int getOuter(Player player) {
        String outer = readString(player, OUTER_KEY);
        if (outer != null && !outer.isBlank()) {
            return resolve(outer, DEFAULT_OUTER, OUTER_DEFAULT_ALPHA);
        }
        // Legacy / 2-arg command: derive mid ribbon from glow when outer is unset.
        return deriveOuter(getGlow(player));
    }

    public static int getGlow(Player player) {
        return resolve(readString(player, GLOW_KEY), DEFAULT_GLOW, GLOW_DEFAULT_ALPHA);
    }

    /**
     * Mid-ribbon color from a glow ARGB: same RGB, higher opacity so the body reads solid.
     */
    public static int deriveOuter(int glowArgb) {
        int rgb = glowArgb & 0xFFFFFF;
        return (OUTER_DEFAULT_ALPHA << 24) | rgb;
    }

    private static String readString(Player player, String key) {
        return BodyAttachments.get(player).getCustomString(player, PART, key);
    }

    /**
     * Parses an {@code RRGGBB} or {@code AARRGGBB} hex string into an ARGB int. When no alpha is
     * supplied, {@code defaultAlpha} is used. Falls back to {@code fallback} on any parse failure.
     */
    public static int resolve(String hex, int fallback, int defaultAlpha) {
        if (hex == null) {
            return fallback;
        }
        String normalized = hex.trim();
        if (normalized.isEmpty()) {
            return fallback;
        }
        if (normalized.startsWith("#")) {
            normalized = normalized.substring(1);
        } else if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            normalized = normalized.substring(2);
        }
        try {
            if (normalized.length() == 6) {
                int rgb = Integer.parseInt(normalized, 16);
                return (defaultAlpha << 24) | (rgb & 0xFFFFFF);
            }
            if (normalized.length() == 8) {
                return (int) Long.parseLong(normalized, 16);
            }
        } catch (NumberFormatException ignored) {
            // fall through to fallback
        }
        return fallback;
    }
}
