package com.github.bandithelps.creation;

import java.util.ArrayList;
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
        CreationUnlockMode unlockMode,
        List<Identifier> unlockVariantIds
) {
    public CreationEntry {
        unlockVariantIds = unlockVariantIds == null ? List.of() : List.copyOf(unlockVariantIds);
        unlockMode = unlockMode == null ? CreationUnlockMode.ABILITY : unlockMode;
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

    public boolean isWoodUnlock() {
        return unlockMode == CreationUnlockMode.WOOD;
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

    public List<Identifier> familyIds() {
        List<Identifier> ids = new ArrayList<>(1 + unlockVariantIds.size());
        ids.add(itemId);
        ids.addAll(unlockVariantIds);
        return ids;
    }
}
