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
        List<String> normalizedGenes = new ArrayList<>();
        String normalizedGene = genesData == null ? "" : genesData.trim();
        if (!normalizedGene.isEmpty() && GeneUtil.parseGene(normalizedGene) != null) {
            normalizedGenes.add(normalizedGene);
        }
        setGenes(stack, normalizedGenes, sourceName, sourceUuid);
    }

    public static void setGenes(ItemStack stack, List<String> genes, String sourceName, String sourceUuid) {
        List<String> normalizedGenes = genes == null
                ? List.of()
                : genes.stream()
                .map(value -> value == null ? "" : value)
                .collect(Collectors.toCollection(ArrayList::new));
        int geneCount = 0;
        for (String gene : normalizedGenes) {
            if (gene != null && !gene.isBlank()) {
                geneCount++;
            }
        }
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag vialTag = new CompoundTag();
        ListTag genesList = new ListTag();
        for (String gene : normalizedGenes) {
            genesList.add(StringTag.valueOf(gene));
        }
        vialTag.put(TAG_GENE_LIST, genesList);
        vialTag.putInt(TAG_GENE_COUNT, geneCount);
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
                genes.add(value);
            }
        }
        return genes;
    }

    public static int getGeneCount(ItemStack stack) {
        int count = 0;
        for (String gene : getStoredGeneList(stack)) {
            if (gene != null && !gene.isBlank()) {
                count++;
            }
        }
        return count;
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

        List<String> slotGenes = getStoredGeneList(stack);
        tooltipAdder.accept(Component.literal("Genes: " + getGeneCount(stack)).withStyle(ChatFormatting.GOLD));
        if (slotGenes.isEmpty()) {
            tooltipAdder.accept(Component.literal("No stored gene slots").withStyle(ChatFormatting.GRAY));
            return;
        }

        for (int i = 0; i < slotGenes.size(); i++) {
            int slotNumber = i + 1;
            String rawGene = slotGenes.get(i);
            if (rawGene == null || rawGene.isBlank()) {
                tooltipAdder.accept(Component.literal("[" + slotNumber + "] Empty").withStyle(ChatFormatting.DARK_GRAY));
                continue;
            }

            Gene parsed = GeneUtil.parseGene(rawGene);
            if (parsed == null) {
                tooltipAdder.accept(Component.literal("[" + slotNumber + "] Invalid gene data").withStyle(ChatFormatting.RED));
                continue;
            }

            Gene resolved = GeneAliasUtil.applyAlias(context.level(), sourceUuid, parsed);
            tooltipAdder.accept(Component.literal("[" + slotNumber + "] " + resolved.getName()
                            + " (" + resolved.getType().getId() + ") q:" + resolved.getQuality())
                    .withStyle(ChatFormatting.YELLOW));
            if (resolved.hasSideEffects()) {
                for (var sideEffect : resolved.getSideEffects()) {
                    tooltipAdder.accept(Component.literal("  - Side effect: " + sideEffect.getDisplayName())
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
    }

}
