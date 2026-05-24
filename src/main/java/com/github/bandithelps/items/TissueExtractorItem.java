package com.github.bandithelps.items;

import com.github.bandithelps.Config;
import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.dna.DNAAttachments;
import com.github.bandithelps.gene.DNA;
import com.github.bandithelps.utils.CommonUtils;
import com.github.bandithelps.utils.gene.GeneUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public class TissueExtractorItem extends Item {
    private static final float USE_RANGE = 4.5f;
    
    public TissueExtractorItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.getCooldowns().isOnCooldown(stack)) {
            return InteractionResult.PASS;
        }

        if (player.isCrouching()) {
            return extractSample(player, player, stack);
        }

        LivingEntity target = CommonUtils.getTargetedLivingEntity(player, USE_RANGE);
        if (target == null) {
            return InteractionResult.PASS;
        }
        return extractSample(player, target, stack);
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

        return extractSample(player, targetEntity, stack);
    }

    private InteractionResult extractSample(Player extractor, LivingEntity targetEntity, ItemStack extractorStack) {
        DNA dna = resolveDNA(targetEntity);
        if (dna == null) {
            extractor.sendSystemMessage(Component.literal("Could not extract DNA from " + targetEntity.getName().getString()));
            return InteractionResult.FAIL;
        }

        ItemStack sampleStack = new ItemStack(YourHeroAcademia.TISSUE_SAMPLE.get());
        TissueSampleItem.setDNA(sampleStack, dna);

        if (!extractor.getInventory().add(sampleStack)) {
            extractor.drop(sampleStack, true);
        }

        extractor.getCooldowns().addCooldown(extractorStack, Config.TISSUE_EXTRACTOR_COOLDOWN.get());
        extractor.sendSystemMessage(Component.literal("Extracted tissue sample from " + targetEntity.getName().getString()));
        return InteractionResult.SUCCESS;
    }

    private DNA resolveDNA(LivingEntity entity) {
        String sourceName = entity.getName().getString();

        if (entity instanceof Player targetPlayer) {
            String dnaString = DNAAttachments.get(targetPlayer).getDNA();
            DNA stored = GeneUtil.parseDNA(dnaString);
            return stored;
        }

        String mobId = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        return GeneUtil.generateDNA(entity.getUUID(), sourceName, mobId);
    }
}