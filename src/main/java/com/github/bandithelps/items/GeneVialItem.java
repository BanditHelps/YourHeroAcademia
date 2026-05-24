package com.github.bandithelps.items;

import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.utils.gene.GeneAliasUtil;
import com.github.bandithelps.utils.gene.GeneUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class GeneVialItem extends Item {
    private static final String TAG_GENES = "genes";
    private static final String TAG_GENE_LIST = "geneList";
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
        List<String> geneList = parseLegacyGeneList(normalizedGenes);
        if (!geneList.isEmpty()) {
            setGenes(stack, geneList, sourceName, sourceUuid);
            return;
        }
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag vialTag = new CompoundTag();
        vialTag.putString(TAG_GENES, normalizedGenes);
        vialTag.putInt(TAG_GENE_COUNT, normalizedGenes.isBlank() ? 0 : normalizedGenes.split(",").length);
        vialTag.putString(TAG_SOURCE_NAME, sourceName == null ? "" : sourceName);
        vialTag.putString(TAG_SOURCE_UUID, sourceUuid == null ? "" : sourceUuid);

        customTag.put("gene_vial", vialTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
    }

    public static void setGenes(ItemStack stack, List<String> genes, String sourceName, String sourceUuid) {
        List<String> normalizedGenes = genes == null
                ? List.of()
                : genes.stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.toCollection(ArrayList::new));
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag vialTag = new CompoundTag();
        ListTag genesList = new ListTag();
        for (String gene : normalizedGenes) {
            genesList.add(StringTag.valueOf(gene));
        }
        vialTag.put(TAG_GENE_LIST, genesList);
        // Legacy field retained for compatibility with older readers.
        vialTag.putString(TAG_GENES, String.join(",", normalizedGenes));
        vialTag.putInt(TAG_GENE_COUNT, normalizedGenes.size());
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
        return String.join(",", getStoredGeneList(stack));
    }

    public static List<String> getStoredGeneList(ItemStack stack) {
        CompoundTag vialTag = getVialData(stack);
        List<String> genes = new ArrayList<>();
        if (vialTag.contains(TAG_GENE_LIST)) {
            ListTag listTag = vialTag.getList(TAG_GENE_LIST).orElse(new ListTag());
            for (int i = 0; i < listTag.size(); i++) {
                String value = listTag.getString(i).orElse("");
                if (!value.isBlank()) {
                    genes.add(value);
                }
            }
            return genes;
        }
        String legacy = vialTag.getString(TAG_GENES).orElse("");
        return parseLegacyGeneList(legacy);
    }

    public static int getGeneCount(ItemStack stack) {
        return getStoredGeneList(stack).size();
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

        List<Gene> genes = parseGenes(getStoredGeneList(stack));
        tooltipAdder.accept(Component.literal("Genes: " + genes.size()).withStyle(ChatFormatting.GOLD));
        for (Gene gene : genes) {
            Gene resolved = GeneAliasUtil.applyAlias(context.level(), sourceUuid, gene);
            tooltipAdder.accept(Component.literal("- " + resolved.getName() + " (" + resolved.getType().getId() + ") q:" + resolved.getQuality())
                    .withStyle(ChatFormatting.YELLOW));
        }
    }

    private static List<Gene> parseGenes(List<String> geneParts) {
        List<Gene> genes = new ArrayList<>();
        if (geneParts == null || geneParts.isEmpty()) {
            return genes;
        }
        for (String genePart : geneParts) {
            Gene gene = GeneUtil.parseGene(genePart);
            if (gene != null) {
                genes.add(gene);
            }
        }
        return genes;
    }

    private static List<String> parseLegacyGeneList(String genesRaw) {
        List<String> genes = new ArrayList<>();
        if (genesRaw == null || genesRaw.isBlank()) {
            return genes;
        }
        String[] geneParts = genesRaw.split(",");
        for (String genePart : geneParts) {
            String trimmed = genePart.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (GeneUtil.parseGene(trimmed) != null) {
                genes.add(trimmed);
            }
        }
        return genes;
    }
}
