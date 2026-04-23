package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.YourHeroAcademiaClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class DNASplicerBlockEvents {
    private DNASplicerBlockEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (state.getBlock() != ModBlocks.DNA_SPLICER.get()) {
            return;
        }

        Player player = event.getEntity();
        ItemStack held = player.getItemInHand(event.getHand());

        if (event.getLevel().isClientSide()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);

        if (held.isEmpty()) {
            return;
        }

        BlockEntity be = event.getLevel().getBlockEntity(pos);
        if (!(be instanceof DNASplicerBlockEntity splicer)) {
            return;
        }

        if (held.getItem() == YourHeroAcademia.TISSUE_SAMPLE.get()) {
            splicer.insertDNA(held);
        } else if (held.getItem() == YourHeroAcademia.GENE_VIAL.get()) {
            splicer.insertVial(held);
        }
    }
}