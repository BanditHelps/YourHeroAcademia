package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.threetag.palladium.network.OpenScreenPacket;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class GeneCombinerBlockEvents {
    private static final Identifier GENE_COMBINER_SCREEN_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "power/gene_combiner");

    private GeneCombinerBlockEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (state.getBlock() != ModBlocks.GENE_COMBINER.get()) {
            return;
        }

        if (event.getLevel().isClientSide()) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
            return;
        }

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        Player player = event.getEntity();
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }

        BlockEntity be = event.getLevel().getBlockEntity(pos);
        if (!(be instanceof GeneCombinerBlockEntity combiner)) {
            return;
        }
        if (!combiner.isUsableBy(serverPlayer)) {
            return;
        }
        combiner.syncToPlayer(serverPlayer);
        PacketDistributor.sendToPlayer(serverPlayer, new OpenScreenPacket(GENE_COMBINER_SCREEN_ID));
    }
}
