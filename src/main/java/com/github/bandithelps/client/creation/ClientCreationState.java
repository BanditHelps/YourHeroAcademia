package com.github.bandithelps.client.creation;

import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationGearKind;
import com.github.bandithelps.creation.CreationPotionForm;
import com.github.bandithelps.creation.CreationPotions;
import com.github.bandithelps.creation.CreationQuickSlot;
import com.github.bandithelps.creation.CreationTab;
import com.github.bandithelps.network.CreationSyncPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ClientCreationState {
    private static CreationSyncPayload latest = emptyPayload();

    private ClientCreationState() {
    }

    public static void apply(CreationSyncPayload payload) {
        latest = payload;
    }

    public static CreationSyncPayload get() {
        return latest;
    }

    public static List<CreationSyncPayload.ClientEntry> entriesForTab(CreationTab tab, boolean unlockedOnly) {
        return entriesForTab(tab, unlockedOnly, null);
    }

    public static List<CreationSyncPayload.ClientEntry> entriesForTab(CreationTab tab, boolean unlockedOnly, CreationGearKind kind) {
        List<CreationSyncPayload.ClientEntry> result = new ArrayList<>();
        for (CreationSyncPayload.ClientEntry entry : latest.entries()) {
            if (CreationTab.fromId(entry.tab()) != tab) {
                continue;
            }
            if (unlockedOnly && !entry.unlocked()) {
                continue;
            }
            if (kind != null && CreationGearKind.of(stackOf(entry.itemId())) != kind) {
                continue;
            }
            result.add(entry);
        }
        return Collections.unmodifiableList(result);
    }

    public static CreationSyncPayload.ClientEntry find(String itemId) {
        if (itemId == null) {
            return null;
        }
        for (CreationSyncPayload.ClientEntry entry : latest.entries()) {
            if (itemId.equals(entry.itemId())) {
                return entry;
            }
        }
        return null;
    }

    public static CreationSyncPayload.ClientEntry findParent(String itemId) {
        if (itemId == null) {
            return null;
        }
        CreationSyncPayload.ClientEntry direct = find(itemId);
        if (direct != null) {
            return direct;
        }
        for (CreationSyncPayload.ClientEntry entry : latest.entries()) {
            if (entry.matches(itemId)) {
                return entry;
            }
        }
        return null;
    }

    public static List<CreationSyncPayload.ClientEnchantEntry> enchants() {
        return latest.enchants();
    }

    public static CreationSyncPayload.ClientEnchantEntry findEnchant(String enchantId) {
        if (enchantId == null) {
            return null;
        }
        for (CreationSyncPayload.ClientEnchantEntry entry : latest.enchants()) {
            if (enchantId.equals(entry.enchantId())) {
                return entry;
            }
        }
        return null;
    }

    public static List<CreationSyncPayload.ClientPotionEntry> potions() {
        return latest.potions() == null ? List.of() : latest.potions();
    }

    public static CreationSyncPayload.ClientPotionEntry findPotion(String effectId) {
        if (effectId == null) {
            return null;
        }
        for (CreationSyncPayload.ClientPotionEntry entry : potions()) {
            if (effectId.equals(entry.effectId())) {
                return entry;
            }
        }
        return null;
    }

    public static List<PotionGroupView> unlockedPotionGroups() {
        Map<String, PotionGroupView> groups = new LinkedHashMap<>();
        for (CreationSyncPayload.ClientPotionEntry entry : potions()) {
            if (!entry.unlocked()) {
                continue;
            }
            String groupId = entry.groupId() == null || entry.groupId().isBlank() ? entry.effectId() : entry.groupId();
            PotionGroupView group = groups.get(groupId);
            if (group == null) {
                group = new PotionGroupView(groupId, entry.groupIcon(), new ArrayList<>());
                groups.put(groupId, group);
            }
            group.effects().add(entry);
        }
        return new ArrayList<>(groups.values());
    }

    public static ItemStack potionStack(String effectId, CreationPotionForm form, int durationTicks, int amplifier) {
        try {
            return CreationPotions.stackOf(Identifier.parse(effectId), form, durationTicks, amplifier);
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static CreationQuickSlot quickSlot(int index) {
        List<String> slots = latest.quickSlots();
        if (index < 0 || index >= slots.size()) {
            return null;
        }
        return CreationQuickSlot.parse(slots.get(index));
    }

    public static ItemStack quickSlotStack(int index, HolderLookup.Provider access) {
        CreationQuickSlot recipe = quickSlot(index);
        return recipe == null ? ItemStack.EMPTY : recipe.iconStack(access);
    }

    public static ItemStack stackOf(String itemId) {
        try {
            return CreationCatalog.stackOf(Identifier.parse(itemId));
        } catch (RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    private static CreationSyncPayload emptyPayload() {
        return new CreationSyncPayload(
                List.of(), List.of(), List.of(), List.of(), List.of(),
                0, false, false, false, false, false, false, false, false, 8
        );
    }

    public record PotionGroupView(String groupId, String iconId, List<CreationSyncPayload.ClientPotionEntry> effects) {
        public boolean isSingleton() {
            return this.effects.size() <= 1;
        }

        public CreationSyncPayload.ClientPotionEntry first() {
            return this.effects.isEmpty() ? null : this.effects.getFirst();
        }
    }
}
