package com.github.bandithelps.creation;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public enum CreationGearKind {
    ARMOR,
    TOOL,
    WEAPON,
    UTILITY;

    public static CreationGearKind of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return UTILITY;
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return ofPath(id == null ? "" : id.getPath());
    }

    public static CreationGearKind ofPath(String path) {
        String value = path == null ? "" : path;
        if (containsAny(value, "helmet", "chestplate", "leggings", "boots", "elytra")) {
            return ARMOR;
        }
        if (containsAny(value, "pickaxe", "shovel", "hoe", "shears")) {
            return TOOL;
        }
        if (containsAny(value, "sword", "axe", "mace", "spear", "trident", "shield")) {
            return WEAPON;
        }
        return UTILITY;
    }

    public String id() {
        return name().toLowerCase();
    }

    private static boolean containsAny(String path, String... tokens) {
        for (String token : tokens) {
            if (path.contains(token)) {
                return true;
            }
        }
        return false;
    }
}
