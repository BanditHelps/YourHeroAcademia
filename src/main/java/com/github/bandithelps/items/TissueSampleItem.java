package com.github.bandithelps.items;

import com.github.bandithelps.gene.DNA;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.gene.SideEffect;
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
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

public class TissueSampleItem extends Item {
    private static final String TAG_DNA = "dna";
    private static final String TAG_SOURCE_NAME = "sourceName";
    private static final String TAG_SOURCE_UUID = "sourceUuid";
    private static final String TAG_GENE_LIST = "geneList";
    private static final String TAG_HARVEST_TIME = "harvestTime";
    private static final DateTimeFormatter HARVEST_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    public TissueSampleItem(Properties properties) {
        super(properties);
    }

    public static void setDNA(ItemStack stack, DNA dna, Level level) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag dnaTag = new CompoundTag();
        dnaTag.putString(TAG_SOURCE_NAME, dna.getSourceName());
        dnaTag.putString(TAG_SOURCE_UUID, dna.getSourceUuid().toString());
        dnaTag.putLong(TAG_HARVEST_TIME, dna.getHarvestTime());
        String sourceUuid = dna.getSourceUuid().toString();
        ListTag genesList = new ListTag();
        for (Gene gene : dna.getGenes()) {
            Gene resolved = GeneAliasUtil.applyAlias(level, sourceUuid, gene);
            genesList.add(StringTag.valueOf(GeneUtil.serializeGene(resolved)));
        }
        dnaTag.put(TAG_GENE_LIST, genesList);

        customTag.put(TAG_DNA, dnaTag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(customTag));
    }

    public static DNA getDNA(ItemStack stack) {
        CompoundTag dnaTag = getDNAData(stack);
        String sourceName = dnaTag.getString(TAG_SOURCE_NAME).orElse("");
        String sourceUuidString = dnaTag.getString(TAG_SOURCE_UUID).orElse("");
        if (sourceName.isEmpty() || sourceUuidString.isEmpty()) {
            return null;
        }

        UUID sourceUuid;
        try {
            sourceUuid = UUID.fromString(sourceUuidString);
        } catch (IllegalArgumentException ex) {
            return null;
        }

        long harvestTime = dnaTag.getLong(TAG_HARVEST_TIME).orElse(System.currentTimeMillis());
        List<Gene> genes = new ArrayList<>();

        ListTag geneListTag = dnaTag.getList(TAG_GENE_LIST).orElse(new ListTag());
        for (int i = 0; i < geneListTag.size(); i++) {
            String genePart = geneListTag.getString(i).orElse("");
            if (genePart.isEmpty()) {
                continue;
            }
            Gene gene = GeneUtil.parseGene(genePart);
            if (gene != null) {
                genes.add(gene);
            }
        }

        return new DNA(sourceName, sourceUuid, genes, harvestTime);
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
        ListTag geneListTag = dnaTag.getList(TAG_GENE_LIST).orElse(new ListTag());
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < geneListTag.size(); i++) {
            String value = geneListTag.getString(i).orElse("");
            if (!value.isEmpty()) {
                parts.add(value);
            }
        }
        return String.join(",", parts);
    }

    public static boolean hasDNA(ItemStack stack) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return customTag.contains(TAG_DNA);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay tooltipDisplay,
                                Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag) {
        DNA dna = getDNA(stack);
        if (dna == null) {
            return;
        }

        tooltipAdder.accept(Component.literal("Source: " + dna.getSourceName()).withStyle(ChatFormatting.AQUA));
        tooltipAdder.accept(Component.literal("UUID: " + dna.getSourceUuid()).withStyle(ChatFormatting.DARK_GRAY));
        tooltipAdder.accept(Component.literal("Harvested: " + HARVEST_TIME_FORMATTER.format(Instant.ofEpochMilli(dna.getHarvestTime())))
                .withStyle(ChatFormatting.GRAY));
        tooltipAdder.accept(Component.literal("Genes: " + dna.getGeneCount()).withStyle(ChatFormatting.GOLD));

        String sourceUuid = dna.getSourceUuid().toString();
        for (Gene gene : dna.getGenes()) {
            Gene resolved = GeneAliasUtil.applyAlias(context.level(), sourceUuid, gene);
            tooltipAdder.accept(Component.literal("- " + resolved.getName() + " (" + resolved.getCategory() + ") q:" + resolved.getQuality())
                    .withStyle(ChatFormatting.YELLOW));
            if (resolved.hasSideEffects()) {
                for (SideEffect sideEffect : resolved.getSideEffects()) {
                    tooltipAdder.accept(Component.literal("  * " + sideEffect.getDisplayName())
                            .withStyle(ChatFormatting.RED));
                }
            }
        }
    }
}