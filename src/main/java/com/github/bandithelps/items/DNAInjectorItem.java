package com.github.bandithelps.items;

import com.github.bandithelps.capabilities.dna.DNAUpdateService;
import com.github.bandithelps.effects.ModEffects;
import com.github.bandithelps.gene.DNA;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.utils.gene.GeneUtil;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

public class DNAInjectorItem extends Item {
    private static final String TAG_DNA = "dna";
    private static final String TAG_SOURCE_NAME = "sourceName";
    private static final String TAG_SOURCE_UUID = "sourceUuid";
    private static final String TAG_GENES = "genes";

    public DNAInjectorItem(Properties properties) {
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

    public static String getGenes(ItemStack stack) {
        CompoundTag dnaTag = getDNAData(stack);
        return dnaTag.getString(TAG_GENES).orElse("");
    }

    public static boolean hasDNA(ItemStack stack) {
        CompoundTag customTag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return customTag.contains(TAG_DNA);
    }

    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (!hasDNA(stack)) {
            return InteractionResult.FAIL;
        }

        String dnaData = getGenes(stack);
        if (dnaData == null || dnaData.isEmpty()) {
            return InteractionResult.FAIL;
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.FAIL;
        }
        DNAUpdateService.setDNA(serverPlayer, dnaData, true);

        int fatigueTicks = 24000;
        player.addEffect(new MobEffectInstance(
                ModEffects.DNA_FATIGUE,
                fatigueTicks,
                0,
                false,
                false,
                true
        ));

        stack.shrink(1);

        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Item.TooltipContext context,
            TooltipDisplay tooltipDisplay,
            Consumer<Component> tooltipAdder,
            TooltipFlag tooltipFlag
    ) {
        if (!hasDNA(stack)) {
            tooltipAdder.accept(Component.literal("Empty Injector").withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltipAdder.accept(Component.literal("Source: " + getSourceName(stack)).withStyle(ChatFormatting.AQUA));
        String dnaRaw = getGenes(stack);
        if (dnaRaw == null || dnaRaw.isBlank()) {
            tooltipAdder.accept(Component.literal("No stored genome data").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        DNA parsedDNA = GeneUtil.parseDNA(dnaRaw);
        if (parsedDNA == null || parsedDNA.isEmpty()) {
            tooltipAdder.accept(Component.literal("Stored DNA is invalid").withStyle(ChatFormatting.RED));
            return;
        }
        List<Gene> genes = parsedDNA.getGenes();
        tooltipAdder.accept(Component.literal("Printed Genes: " + genes.size()).withStyle(ChatFormatting.GOLD));
        for (int i = 0; i < genes.size(); i++) {
            Gene gene = genes.get(i);
            tooltipAdder.accept(Component.literal("[" + (i + 1) + "] " + gene.getName()
                            + " (" + gene.getType().getId() + ", q:" + gene.getQuality() + ")")
                    .withStyle(ChatFormatting.YELLOW));
            if (gene.hasSideEffects()) {
                gene.getSideEffects().forEach(effect ->
                        tooltipAdder.accept(Component.literal("   - Side effect: " + effect.getDisplayName())
                                .withStyle(ChatFormatting.RED)));
            }
        }
    }

}