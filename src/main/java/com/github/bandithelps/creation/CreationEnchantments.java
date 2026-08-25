package com.github.bandithelps.creation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

public final class CreationEnchantments {
    private CreationEnchantments() {
    }

    public static Optional<Holder<Enchantment>> holder(HolderLookup.Provider provider, Identifier enchantId) {
        if (provider == null || enchantId == null) {
            return Optional.empty();
        }
        return provider.lookup(Registries.ENCHANTMENT)
                .flatMap(lookup -> lookup.get(ResourceKey.create(Registries.ENCHANTMENT, enchantId)))
                .map(reference -> reference);
    }

    public static int vanillaMaxLevel(HolderLookup.Provider provider, Identifier enchantId) {
        return holder(provider, enchantId)
                .map(value -> Math.max(1, value.value().getMaxLevel()))
                .orElse(1);
    }

    public static boolean canEnchant(HolderLookup.Provider provider, Identifier enchantId, ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        Holder<Enchantment> enchantment = holder(provider, enchantId).orElse(null);
        return enchantment != null && enchantment.value().isSupportedItem(stack);
    }

    public static boolean compatible(HolderLookup.Provider provider, Identifier leftId, Identifier rightId) {
        if (leftId == null || rightId == null || leftId.equals(rightId)) {
            return true;
        }
        Holder<Enchantment> left = holder(provider, leftId).orElse(null);
        Holder<Enchantment> right = holder(provider, rightId).orElse(null);
        if (left == null || right == null) {
            return true;
        }
        return !left.value().exclusiveSet().contains(right) && !right.value().exclusiveSet().contains(left);
    }

    public static boolean compatibleWith(HolderLookup.Provider provider, Identifier candidateId, Map<Identifier, Integer> selected) {
        if (selected == null || selected.isEmpty()) {
            return true;
        }
        for (Map.Entry<Identifier, Integer> entry : selected.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            if (!compatible(provider, candidateId, entry.getKey())) {
                return false;
            }
        }
        return true;
    }

    public static Component displayName(HolderLookup.Provider provider, Identifier enchantId) {
        return holder(provider, enchantId)
                .map(value -> value.value().description())
                .orElseGet(() -> Component.literal(enchantId == null ? "" : enchantId.toString()));
    }

    public static ItemStack bookPreview(HolderLookup.Provider provider, Identifier enchantId, int level) {
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        Holder<Enchantment> enchantment = holder(provider, enchantId).orElse(null);
        if (enchantment == null) {
            return book;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(enchantment, Math.max(1, level));
        book.set(DataComponents.STORED_ENCHANTMENTS, mutable.toImmutable());
        return book;
    }

    public static void apply(ItemStack stack, HolderLookup.Provider provider, Map<Identifier, Integer> levels) {
        if (stack == null || stack.isEmpty() || provider == null || levels == null || levels.isEmpty()) {
            return;
        }
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        boolean any = false;
        for (Map.Entry<Identifier, Integer> entry : levels.entrySet()) {
            if (entry.getValue() == null || entry.getValue() <= 0) {
                continue;
            }
            Holder<Enchantment> enchantment = holder(provider, entry.getKey()).orElse(null);
            if (enchantment == null) {
                continue;
            }
            mutable.set(enchantment, entry.getValue());
            any = true;
        }
        if (any) {
            stack.set(DataComponents.ENCHANTMENTS, mutable.toImmutable());
        }
    }

    public static List<Identifier> storedEnchantIds(ItemStack stack) {
        List<Identifier> ids = new ArrayList<>();
        if (stack == null || stack.isEmpty()) {
            return ids;
        }
        ItemEnchantments stored = stack.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (stored.isEmpty()) {
            return ids;
        }
        for (Holder<Enchantment> enchantment : stored.keySet()) {
            enchantment.unwrapKey().ifPresent(key -> ids.add(key.identifier()));
        }
        return ids;
    }

    public static boolean bookContains(ItemStack stack, Identifier enchantId) {
        if (enchantId == null || stack == null || stack.isEmpty() || !stack.is(Items.ENCHANTED_BOOK)) {
            return false;
        }
        return storedEnchantIds(stack).contains(enchantId);
    }

    public static int countBooksContaining(Player player, Identifier enchantId) {
        if (player == null || enchantId == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (bookContains(slot, enchantId)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    public static ItemStack consumeBookContaining(Player player, Identifier enchantId) {
        if (player == null || enchantId == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!bookContains(slot, enchantId)) {
                continue;
            }
            ItemStack taken = slot.copyWithCount(1);
            slot.shrink(1);
            return taken;
        }
        return ItemStack.EMPTY;
    }
}
