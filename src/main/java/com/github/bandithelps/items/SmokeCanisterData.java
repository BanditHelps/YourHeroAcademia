package com.github.bandithelps.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.alchemy.PotionContents;
import org.jspecify.annotations.Nullable;

import java.util.List;

public final class SmokeCanisterData {
    public static final String TAG_ROOT = "yha_smoke_canister";
    private static final String TAG_TIER = "tier";

    public static final int MIN_TIER = 0;
    public static final int MAX_TIER = 3;

    private SmokeCanisterData() {
    }

    public static int getTier(ItemStack stack) {
        CompoundTag root = getRootTag(stack);
        return clampTier(root.getInt(TAG_TIER).orElse(MIN_TIER));
    }

    public static void setTier(ItemStack stack, int tier) {
        int clampedTier = clampTier(tier);
        CompoundTag root = getRootTag(stack);
        root.putInt(TAG_TIER, clampedTier);
        saveRootTag(stack, root);
        updateCustomModelData(stack, clampedTier);
    }

    public static int increaseTier(ItemStack stack, int amount) {
        int newTier = clampTier(getTier(stack) + Math.max(0, amount));
        setTier(stack, newTier);
        return newTier;
    }

    public static @Nullable PotionContents getPotionContents(ItemStack stack) {
        return stack.get(DataComponents.POTION_CONTENTS);
    }

    public static void setPotionContents(ItemStack stack, @Nullable PotionContents potionContents) {
        if (potionContents == null) {
            stack.remove(DataComponents.POTION_CONTENTS);
            return;
        }
        stack.set(DataComponents.POTION_CONTENTS, potionContents);
    }

    private static CompoundTag getRootTag(ItemStack stack) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return customTag.getCompound(TAG_ROOT).orElse(new CompoundTag());
    }

    private static void saveRootTag(ItemStack stack, CompoundTag rootTag) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        customTag.put(TAG_ROOT, rootTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
    }

    private static int clampTier(int tier) {
        return Math.max(MIN_TIER, Math.min(MAX_TIER, tier));
    }

    private static void updateCustomModelData(ItemStack stack, int tier) {
        if (tier <= MIN_TIER) {
            stack.remove(DataComponents.CUSTOM_MODEL_DATA);
            return;
        }
        stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(
                List.of(),
                List.of(),
                List.of(String.valueOf(tier)),
                List.of()
        ));
    }
}
