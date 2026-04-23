package com.github.bandithelps.items;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.UUID;

public class TissueExtractorItem extends Item {
    private static final int DEFAULT_COOLDOWN = 30;
    
    public TissueExtractorItem(Properties properties) {
        super(properties);
    }

    public InteractionResult onItemUseFirst(Item item, Level level, Player player, InteractionHand hand, Entity targetEntity) {
        if (!(targetEntity instanceof Player targetPlayer)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }

        UUID targetUUID = targetPlayer.getUUID();
        String dnaData = generateSimpleDNA(targetUUID);

        ItemStack sampleStack = new ItemStack(YourHeroAcademia.TISSUE_SAMPLE.get());
        TissueSampleItem.setDNA(sampleStack, targetPlayer.getName().getString(), targetUUID.toString(), dnaData);

        if (!player.getInventory().add(sampleStack)) {
            targetPlayer.drop(sampleStack, true);
        }

        player.getCooldowns().addCooldown(stack, DEFAULT_COOLDOWN);

        return InteractionResult.SUCCESS;
    }

    private String generateSimpleDNA(UUID uuid) {
        RandomSource random = RandomSource.create(uuid.hashCode());
        StringBuilder dnaBuilder = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (i > 0) dnaBuilder.append(",");
            dnaBuilder.append("gene_").append(i).append(":").append(1 + random.nextInt(5));
        }
        return dnaBuilder.toString();
    }
}