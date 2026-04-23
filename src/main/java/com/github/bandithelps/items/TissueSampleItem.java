package com.github.bandithelps.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class TissueSampleItem extends Item {
    private static final String TAG_DNA = "dna";
    private static final String TAG_SOURCE_NAME = "sourceName";
    private static final String TAG_SOURCE_UUID = "sourceUuid";
    private static final String TAG_GENES = "genes";

    public TissueSampleItem(Properties properties) {
        super(properties);
    }

    public static void setDNA(ItemStack stack, String sourceName, String sourceUuid, String dnaData) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag dnaTag = new CompoundTag();
        dnaTag.putString(TAG_SOURCE_NAME, sourceName);
        dnaTag.putString(TAG_SOURCE_UUID, sourceUuid);
        dnaTag.putString(TAG_GENES, dnaData);

        customTag.put(TAG_DNA, dnaTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
    }

    public static CompoundTag getDNAData(ItemStack stack) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return customTag.getCompound(TAG_DNA).orElse(new CompoundTag());
    }

    public static String getSourceName(ItemStack stack) {
        CompoundTag dnaTag = getDNAData(stack);
        return dnaTag.getString(TAG_SOURCE_NAME).orElse("Unknown");
    }

    public static String getSourceUuid(ItemStack stack) {
        CompoundTag dnaTag = getDNAData(stack);
        return dnaTag.getString(TAG_SOURCE_UUID).orElse("");
    }

    public static String getGenesData(ItemStack stack) {
        CompoundTag dnaTag = getDNAData(stack);
        return dnaTag.getString(TAG_GENES).orElse("");
    }

    public static boolean hasDNA(ItemStack stack) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return customTag.contains(TAG_DNA);
    }
}