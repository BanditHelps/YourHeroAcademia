package com.github.bandithelps.creation;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;

public final class CreationWoodTypes {
    private static final List<String> WOODS = List.of(
            "pale_oak",
            "dark_oak",
            "mangrove",
            "cherry",
            "bamboo",
            "crimson",
            "warped",
            "spruce",
            "birch",
            "jungle",
            "acacia",
            "oak"
    );
    private static final List<String> SOURCE_SUFFIXES = List.of(
            "_log",
            "_stem",
            "_block",
            "_planks",
            "_wood",
            "_hyphae"
    );

    private CreationWoodTypes() {
    }

    public static String woodKey(Identifier itemId) {
        if (itemId == null) {
            return null;
        }
        return woodKey(itemId.getPath());
    }

    public static String woodKey(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        String normalized = path;
        if (normalized.startsWith("hanging_")) {
            normalized = normalized.substring("hanging_".length());
        }
        for (String wood : WOODS) {
            if (normalized.equals(wood) || normalized.startsWith(wood + "_")) {
                return wood;
            }
        }
        return null;
    }

    public static boolean isWoodKnown(Set<Identifier> unlocked, Identifier productId) {
        if (unlocked == null || productId == null) {
            return false;
        }
        String wood = woodKey(productId);
        if (wood == null) {
            return false;
        }
        for (String suffix : SOURCE_SUFFIXES) {
            if (unlocked.contains(Identifier.fromNamespaceAndPath("minecraft", wood + suffix))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isWoodKnown(Collection<String> unlocked, String productId) {
        if (unlocked == null || productId == null || productId.isBlank()) {
            return false;
        }
        try {
            Identifier id = Identifier.parse(productId);
            String wood = woodKey(id);
            if (wood == null) {
                return false;
            }
            for (String suffix : SOURCE_SUFFIXES) {
                if (unlocked.contains("minecraft:" + wood + suffix)) {
                    return true;
                }
            }
            return false;
        } catch (RuntimeException ignored) {
            return false;
        }
    }
}
