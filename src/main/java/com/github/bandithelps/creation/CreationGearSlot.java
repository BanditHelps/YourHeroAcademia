package com.github.bandithelps.creation;

import java.util.Comparator;
import java.util.Locale;
import net.minecraft.resources.Identifier;

public enum CreationGearSlot {
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS,
    PICKAXE,
    AXE,
    SHOVEL,
    HOE,
    SWORD,
    SHIELD,
    RANGED,
    HEAVY,
    SHEARS,
    FISHING_ROD;

    public static CreationGearSlot of(String itemId) {
        String path = pathOf(itemId);
        if (path.isEmpty()) {
            return null;
        }
        if (path.contains("helmet")) {
            return HELMET;
        }
        if (path.contains("chestplate")) {
            return CHESTPLATE;
        }
        if (path.contains("leggings")) {
            return LEGGINGS;
        }
        if (path.contains("boots")) {
            return BOOTS;
        }
        if (path.contains("pickaxe")) {
            return PICKAXE;
        }
        if (path.contains("shovel")) {
            return SHOVEL;
        }
        if (path.contains("hoe")) {
            return HOE;
        }
        if (path.contains("axe") && !path.contains("pickaxe")) {
            return AXE;
        }
        if (path.contains("sword")) {
            return SWORD;
        }
        if (path.contains("shield")) {
            return SHIELD;
        }
        if (path.equals("bow") || path.equals("crossbow") || path.endsWith("_bow")) {
            return RANGED;
        }
        if (path.contains("spear") || path.equals("mace") || path.endsWith("_mace")) {
            return HEAVY;
        }
        if (path.equals("shears")) {
            return SHEARS;
        }
        if (path.equals("fishing_rod")) {
            return FISHING_ROD;
        }
        return null;
    }

    public boolean matches(String itemId) {
        return this == of(itemId);
    }

    public static int variantOrder(String itemId) {
        String path = pathOf(itemId);
        if (path.contains("leather")) {
            return 0;
        }
        if (path.contains("wood")) {
            return 1;
        }
        if (path.contains("stone")) {
            return 2;
        }
        if (path.contains("copper")) {
            return 3;
        }
        if (path.contains("gold")) {
            return 4;
        }
        if (path.contains("iron")) {
            return 5;
        }
        if (path.contains("diamond")) {
            return 6;
        }
        if (path.contains("netherite")) {
            return 7;
        }
        if (path.equals("bow")) {
            return 8;
        }
        if (path.equals("crossbow")) {
            return 9;
        }
        if (path.contains("spear")) {
            return 10;
        }
        if (path.contains("mace")) {
            return 11;
        }
        return 50;
    }

    public static Comparator<String> variantComparator() {
        return Comparator.comparingInt(CreationGearSlot::variantOrder).thenComparing(value -> value);
    }

    private static String pathOf(String itemId) {
        if (itemId == null || itemId.isBlank()) {
            return "";
        }
        try {
            return Identifier.parse(itemId).getPath().toLowerCase(Locale.ROOT);
        } catch (RuntimeException ignored) {
            int slash = itemId.indexOf(':');
            return (slash >= 0 ? itemId.substring(slash + 1) : itemId).toLowerCase(Locale.ROOT);
        }
    }
}
