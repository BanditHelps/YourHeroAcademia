package com.github.bandithelps.creation;

import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;

public final class CreationPotions {
    private CreationPotions() {
    }

    public static Optional<Holder<MobEffect>> holder(Identifier effectId) {
        if (effectId == null) {
            return Optional.empty();
        }
        return BuiltInRegistries.MOB_EFFECT.get(effectId).map(value -> value);
    }

    public static boolean isInstant(Identifier effectId, Boolean override) {
        if (override != null) {
            return override;
        }
        return holder(effectId).map(value -> value.value().isInstantenous()).orElse(false);
    }

    public static ItemStack stackOf(Identifier effectId, CreationPotionForm form, int durationTicks, int amplifier) {
        Holder<MobEffect> effect = holder(effectId).orElse(null);
        if (effect == null) {
            return ItemStack.EMPTY;
        }
        CreationPotionForm resolved = form != null ? form : CreationPotionForm.DRINKABLE;
        int ticks = isInstant(effectId, null) ? 1 : Math.max(1, durationTicks);
        int amp = Math.max(0, amplifier);
        MobEffectInstance instance = new MobEffectInstance(effect, ticks, amp);
        int color = effect.value().getColor();
        PotionContents contents = new PotionContents(
                Optional.empty(),
                Optional.of(color),
                List.of(instance),
                Optional.empty()
        );
        ItemStack stack = new ItemStack(resolved.item());
        stack.set(DataComponents.POTION_CONTENTS, contents);
        Component name = itemName(effectId, resolved);
        stack.set(DataComponents.ITEM_NAME, name);
        stack.set(DataComponents.CUSTOM_NAME, name);
        return stack;
    }

    public static Component itemName(Identifier effectId, CreationPotionForm form) {
        CreationPotionForm resolved = form != null ? form : CreationPotionForm.DRINKABLE;
        return Component.translatable(resolved.itemNameKey(), displayName(effectId));
    }

    public static Component displayName(Identifier effectId) {
        return holder(effectId)
                .map(value -> Component.translatable(value.value().getDescriptionId()))
                .orElseGet(() -> Component.literal(effectId == null ? "" : effectId.toString()));
    }

    public static ItemStack previewStack(Identifier effectId) {
        return stackOf(effectId, CreationPotionForm.DRINKABLE, CreationPotionEntry.DEFAULT_DURATION_SECONDS * 20, 0);
    }

    public static boolean isPotionLike(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        return stack.is(Items.POTION)
                || stack.is(Items.SPLASH_POTION)
                || stack.is(Items.LINGERING_POTION)
                || stack.is(Items.TIPPED_ARROW);
    }

    public static boolean containsEffect(ItemStack stack, Identifier effectId) {
        if (!isPotionLike(stack) || effectId == null) {
            return false;
        }
        PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
        if (contents == null) {
            return false;
        }
        for (MobEffectInstance instance : contents.getAllEffects()) {
            Identifier id = BuiltInRegistries.MOB_EFFECT.getKey(instance.getEffect().value());
            if (effectId.equals(id)) {
                return true;
            }
        }
        return false;
    }

    public static int countContaining(net.minecraft.world.entity.player.Player player, Identifier effectId) {
        if (player == null || effectId == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (containsEffect(slot, effectId)) {
                count += slot.getCount();
            }
        }
        return count;
    }

    public static boolean consumeOneContaining(net.minecraft.world.entity.player.Player player, Identifier effectId) {
        if (player == null || effectId == null) {
            return false;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack slot = player.getInventory().getItem(i);
            if (!containsEffect(slot, effectId)) {
                continue;
            }
            slot.shrink(1);
            return true;
        }
        return false;
    }

    public static int romanAmplifier(int amplifier) {
        return Mth.clamp(amplifier, 0, 64) + 1;
    }
}
