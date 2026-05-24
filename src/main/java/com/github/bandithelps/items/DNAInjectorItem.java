package com.github.bandithelps.items;

import com.github.bandithelps.capabilities.dna.DNAAttachments;
import com.github.bandithelps.effects.ModEffects;
import com.github.bandithelps.gene.GeneEffectHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.effect.MobEffectInstance;

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

        String genes = getGenes(stack);
        if (genes == null || genes.isEmpty()) {
            return InteractionResult.FAIL;
        }

        var dna = DNAAttachments.get(player);
        dna.setDNA(genes);
        dna.setDNAFatigued(true);

        GeneEffectHandler.applyGeneEffects(player);

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
}