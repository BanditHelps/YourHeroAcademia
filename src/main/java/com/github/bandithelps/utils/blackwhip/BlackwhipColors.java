package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.entities.BlackwhipEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Resolves a player's customized Blackwhip colors from the body-data string system. Colors are stored
 * as {@code RRGGBB} (or {@code AARRGGBB}) hex strings on the chest under
 * {@link #CORE_KEY} / {@link #GLOW_KEY} and are set via the {@code /yha blackwhip color} command.
 */
public final class BlackwhipColors {

    public static final BodyPart PART = BodyPart.CHEST;
    public static final String CORE_KEY = "blackwhip_color_core";
    public static final String GLOW_KEY = "blackwhip_color_glow";

    /** Default canon colors: near-black core, translucent teal glow. */
    public static final int DEFAULT_CORE = BlackwhipEntity.DEFAULT_CORE;
    public static final int DEFAULT_GLOW = BlackwhipEntity.DEFAULT_GLOW;

    private static final int CORE_DEFAULT_ALPHA = 0xFF;
    private static final int GLOW_DEFAULT_ALPHA = 0xB3;

    private BlackwhipColors() {
    }

    public static int getCore(Player player) {
        return resolve(readString(player, CORE_KEY), DEFAULT_CORE, CORE_DEFAULT_ALPHA);
    }

    public static int getGlow(Player player) {
        return resolve(readString(player, GLOW_KEY), DEFAULT_GLOW, GLOW_DEFAULT_ALPHA);
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
