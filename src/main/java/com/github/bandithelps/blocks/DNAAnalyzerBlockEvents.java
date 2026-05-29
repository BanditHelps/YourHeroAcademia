package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.threetag.palladium.network.OpenScreenPacket;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class DNAAnalyzerBlockEvents {
    private static final Identifier DNA_ANALYZER_SCREEN_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "power/dna_analyzer");

    private DNAAnalyzerBlockEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (state.getBlock() != ModBlocks.DNA_ANALYZER.get()) {
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

        BlockEntity be = event.getLevel().getBlockEntity(pos);
        if (be instanceof DNAAnalyzerBlockEntity analyzer && player instanceof ServerPlayer serverPlayer) {
            boolean wantsRetrieveSample = player.isShiftKeyDown() && held.isEmpty();
            if (wantsRetrieveSample && analyzer.retrieveSample(player, event.getHand())) {
                return;
            }

            boolean wantsCollect = !held.isEmpty() && held.getItem() == YourHeroAcademia.EMPTY_GENE_VIAL.get();
            if (wantsCollect && analyzer.collectProcessedGenes(held, player, event.getHand())) {
                return;
            }

            boolean wantsInsert = player.isShiftKeyDown()
                    && !held.isEmpty()
                    && held.getItem() == YourHeroAcademia.TISSUE_SAMPLE.get();
            if (wantsInsert && analyzer.insertSample(held, player)) {
                return;
            }

            analyzer.syncToPlayer(serverPlayer);
            PacketDistributor.sendToPlayer(serverPlayer, new OpenScreenPacket(DNA_ANALYZER_SCREEN_ID));
        }
    }
}