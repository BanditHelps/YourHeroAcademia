package com.github.bandithelps.items;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public class GeneVialItem extends Item {
    private static final String TAG_GENES = "genes";
    private static final String TAG_GENE_COUNT = "geneCount";

    public GeneVialItem(Properties properties) {
        super(properties);
    }

    public static void setGenes(ItemStack stack, String genesData) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag vialTag = new CompoundTag();
        vialTag.putString(TAG_GENES, genesData);
        vialTag.putInt(TAG_GENE_COUNT, genesData.split(",").length);

        customTag.put("gene_vial", vialTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
    }

    public static CompoundTag getVialData(ItemStack stack) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return customTag.getCompound("gene_vial").orElse(new CompoundTag());
    }

    public static String getStoredGenes(ItemStack stack) {
        CompoundTag vialTag = getVialData(stack);
        return vialTag.getString(TAG_GENES).orElse("");
    }

    public static int getGeneCount(ItemStack stack) {
        CompoundTag vialTag = getVialData(stack);
        return vialTag.getInt(TAG_GENE_COUNT).orElse(0);
    }

    public static boolean hasGenes(ItemStack stack) {
        return getGeneCount(stack) > 0;
    }

    public static boolean canAddGene(ItemStack stack) {
        return getGeneCount(stack) < 3;
    }
}