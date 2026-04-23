package com.github.bandithelps.utils.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

public final class PlayerUtils {
    private PlayerUtils() {
    }

    public static void giveItem(Player player, ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        var inv = player.getInventory();
        if (inv.add(stack)) {
            return;
        }
        player.drop(stack, false);
    }
}