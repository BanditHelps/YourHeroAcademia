package com.github.bandithelps.client.creation;

import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationGearKind;
import com.github.bandithelps.creation.CreationPotionForm;
import com.github.bandithelps.creation.CreationPotions;
import com.github.bandithelps.creation.CreationQuickSlot;
import com.github.bandithelps.creation.CreationTab;
import com.github.bandithelps.creation.CreationWoodTypes;
import com.github.bandithelps.network.CreationSyncPayload;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public final class ClientCreationState {
    private static final List<String> MATERIAL_STRIP = List.of(
            "minecraft:flint",
            "minecraft:copper_ingot",
            "minecraft:coal",
            "minecraft:charcoal",
            "minecraft:iron_ingot",
            "palladium:lead",
            "minecraft:redstone",
            "minecraft:gold_ingot",
            "minecraft:lapis_lazuli",
            "minecraft:diamond",
            "minecraft:emerald",
            "minecraft:netherite_ingot"
    );
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

    public static List<ItemGroupView> unlockedItemGroups(CreationTab tab) {
        Map<String, ItemGroupView> groups = new LinkedHashMap<>();
        List<String> unlockedIds = latest.unlocked() == null ? List.of() : latest.unlocked();
        for (CreationSyncPayload.ClientEntry entry : entriesForTab(tab, true)) {
            String groupId = entry.groupId() == null || entry.groupId().isBlank() ? entry.itemId() : entry.groupId();
            ItemGroupView group = groups.get(groupId);
            if (group == null) {
                String iconId = entry.groupIcon() == null || entry.groupIcon().isBlank() ? entry.itemId() : entry.groupIcon();
                group = new ItemGroupView(groupId, iconId, new ArrayList<>());
                groups.put(groupId, group);
            }
            if (entry.woodVariants()) {
                if (CreationWoodTypes.isWoodKnown(unlockedIds, entry.itemId())) {
                    addGroupItem(group.itemIds(), entry.itemId());
                }
                for (String variantId : entry.unlockVariantIds()) {
                    if (CreationWoodTypes.isWoodKnown(unlockedIds, variantId)) {
                        addGroupItem(group.itemIds(), variantId);
                    }
                }
            } else {
                addGroupItem(group.itemIds(), entry.itemId());
                for (String variantId : entry.unlockVariantIds()) {
                    addGroupItem(group.itemIds(), variantId);
                }
            }
        }
        List<ItemGroupView> result = new ArrayList<>();
        for (ItemGroupView group : groups.values()) {
            if (!group.itemIds().isEmpty()) {
                result.add(group);
            }
        }
        if (tab == CreationTab.MATERIALS) {
            result.sort(Comparator.comparingInt(ClientCreationState::materialStripIndex));
        }
        return result;
    }

    private static int materialStripIndex(ItemGroupView group) {
        int best = Integer.MAX_VALUE;
        for (String itemId : group.itemIds()) {
            int index = MATERIAL_STRIP.indexOf(itemId);
            if (index >= 0 && index < best) {
                best = index;
            }
        }
        return best;
    }

    private static void addGroupItem(List<String> itemIds, String itemId) {
        if (itemId == null || itemId.isBlank() || itemIds.contains(itemId)) {
            return;
        }
        itemIds.add(itemId);
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

    public record ItemGroupView(String groupId, String iconId, List<String> itemIds) {
        public boolean isSingleton() {
            return this.itemIds.size() <= 1;
        }

        public String firstItemId() {
            return this.itemIds.isEmpty() ? null : this.itemIds.getFirst();
        }

        public boolean contains(String itemId) {
            return itemId != null && this.itemIds.contains(itemId);
        }
    }
}
