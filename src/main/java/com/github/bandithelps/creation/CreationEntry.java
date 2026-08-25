package com.github.bandithelps.creation;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record CreationEntry(
        Identifier itemId,
        CreationTab tab,
        String abilityKey,
        int lipidCost
) {
    public ItemStack stack() {
        return CreationCatalog.stackOf(itemId);
    }

    public Item item() {
        return stack().getItem();
    }
}
