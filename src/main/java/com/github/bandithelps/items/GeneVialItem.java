package com.github.bandithelps.items;

import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.utils.gene.GeneUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class GeneVialItem extends Item {
    private static final String TAG_GENES = "genes";
    private static final String TAG_GENE_COUNT = "geneCount";
    private static final String TAG_SOURCE_NAME = "sourceName";
    private static final String TAG_SOURCE_UUID = "sourceUuid";

    public GeneVialItem(Properties properties) {
        super(properties);
    }

    public static void setGenes(ItemStack stack, String genesData) {
        setGenes(stack, genesData, "", "");
    }

    public static void setGenes(ItemStack stack, String genesData, String sourceName, String sourceUuid) {
        String normalizedGenes = genesData == null ? "" : genesData;
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag vialTag = new CompoundTag();
        vialTag.putString(TAG_GENES, normalizedGenes);
        vialTag.putInt(TAG_GENE_COUNT, normalizedGenes.isBlank() ? 0 : normalizedGenes.split(",").length);
        vialTag.putString(TAG_SOURCE_NAME, sourceName == null ? "" : sourceName);
        vialTag.putString(TAG_SOURCE_UUID, sourceUuid == null ? "" : sourceUuid);

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

    public static String getSourceName(ItemStack stack) {
        CompoundTag vialTag = getVialData(stack);
        return vialTag.getString(TAG_SOURCE_NAME).orElse("");
    }

    public static String getSourceUuid(ItemStack stack) {
        CompoundTag vialTag = getVialData(stack);
        return vialTag.getString(TAG_SOURCE_UUID).orElse("");
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay tooltipDisplay,
            Consumer<Component> tooltipAdder,
            TooltipFlag tooltipFlag
    ) {
        String sourceName = getSourceName(stack);
        String sourceUuid = getSourceUuid(stack);
        if (!sourceName.isBlank()) {
            tooltipAdder.accept(Component.literal("Source: " + sourceName).withStyle(ChatFormatting.AQUA));
        }
        if (!sourceUuid.isBlank()) {
            tooltipAdder.accept(Component.literal("UUID: " + sourceUuid).withStyle(ChatFormatting.DARK_GRAY));
        }

        String genesRaw = getStoredGenes(stack);
        List<Gene> genes = parseGenes(genesRaw);
        tooltipAdder.accept(Component.literal("Genes: " + genes.size()).withStyle(ChatFormatting.GOLD));
        for (Gene gene : genes) {
            tooltipAdder.accept(Component.literal("- " + gene.getName() + " (" + gene.getType().getId() + ") q:" + gene.getQuality())
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static List<Gene> parseGenes(String genesRaw) {
        List<Gene> genes = new ArrayList<>();
        if (genesRaw == null || genesRaw.isBlank()) {
            return genes;
        }
        String[] geneParts = genesRaw.split(",");
        for (String genePart : geneParts) {
            Gene gene = GeneUtil.parseGene(genePart);
            if (gene != null) {
                genes.add(gene);
            }
        }
        return genes;
    }
}
