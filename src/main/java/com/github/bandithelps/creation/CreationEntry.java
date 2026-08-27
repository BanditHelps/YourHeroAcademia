package com.github.bandithelps.creation;

import java.util.List;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record CreationEntry(
        Identifier itemId,
        CreationTab tab,
        String abilityKey,
        int lipidCost,
        int researchCost,
        Identifier nuggetId,
        Identifier blockId,
        Identifier groupId,
        Identifier groupIcon,
        String unlockAbility,
        List<Identifier> unlockVariantIds
) {
    public CreationEntry {
        unlockVariantIds = unlockVariantIds == null ? List.of() : List.copyOf(unlockVariantIds);
    }

    public ItemStack stack() {
        return CreationCatalog.stackOf(itemId);
    }

    public Item item() {
        return stack().getItem();
    }

    public boolean hasForms() {
        return nuggetId != null || blockId != null;
    }

    public boolean isUnlockVariant(Identifier requested) {
        return requested != null && unlockVariantIds.contains(requested);
    }

    public boolean isKnownForm(Identifier requested) {
        if (requested == null) {
            return false;
        }
        return requested.equals(itemId)
                || requested.equals(nuggetId)
                || requested.equals(blockId)
                || isUnlockVariant(requested);
    }
}
