package com.github.bandithelps.items;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.gene.DNA;
import com.github.bandithelps.utils.gene.GeneUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TissueExtractorItem extends Item {
    private static final int DEFAULT_COOLDOWN = 30;
    private final float USE_RANGE = 4.5f;
    
    public TissueExtractorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
    }

    // Temp logic. Will be used later to see if the entity has valid genes
    private boolean isValidTarget(LivingEntity target) {
        return true;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity targetEntity, InteractionHand hand) {
        if (!isValidTarget(targetEntity)) {
            return InteractionResult.PASS;
        }

        Level level = player.level();
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }

        String entityName = targetEntity.getName().getString();
        DNA dna = GeneUtil.generateDNA(targetEntity.getUUID(), entityName);

        ItemStack sampleStack = new ItemStack(YourHeroAcademia.TISSUE_SAMPLE.get());
        TissueSampleItem.setDNA(sampleStack, dna);

        if (!player.getInventory().add(sampleStack)) {
            player.drop(sampleStack, true);
        }

        player.getCooldowns().addCooldown(stack, DEFAULT_COOLDOWN);
        player.sendSystemMessage(Component.literal("Extracted tissue sample from " + entityName));

        return InteractionResult.SUCCESS;
    }
}