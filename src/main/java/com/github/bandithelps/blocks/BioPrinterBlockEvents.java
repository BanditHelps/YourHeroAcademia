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
public final class BioPrinterBlockEvents {
    private static final Identifier BIO_PRINTER_SCREEN_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "power/bio_printer");

    private BioPrinterBlockEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }

        BlockPos pos = event.getPos();
        BlockState state = event.getLevel().getBlockState(pos);
        if (state.getBlock() != ModBlocks.BIO_PRINTER.get()) {
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
        if (!(be instanceof BioPrinterBlockEntity printer)) {
            return;
        }

        boolean wantsExtract = player.isShiftKeyDown() && printer.extractToInjector(serverPlayer);
        if (wantsExtract) {
            return;
        }

        printer.syncToPlayer(serverPlayer);
        PacketDistributor.sendToPlayer(serverPlayer, new OpenScreenPacket(BIO_PRINTER_SCREEN_ID));
    }
}
