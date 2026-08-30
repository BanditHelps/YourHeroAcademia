package com.github.bandithelps.blocks;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.capabilities.creation.CreationSyncEvents;
import com.github.bandithelps.creation.CreationCatalog;
import com.github.bandithelps.creation.CreationUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.threetag.palladium.network.OpenScreenPacket;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class ResearchTableBlockEvents {
    public static final Identifier SCREEN_ID =
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "power/research_table");

    private ResearchTableBlockEvents() {
    }

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (state.getBlock() != ModBlocks.RESEARCH_TABLE.get()) {
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
        if (!CreationUtil.hasCreation(serverPlayer)) {
            serverPlayer.sendSystemMessage(Component.translatable("gui.yha.creation.need_quirk"));
            return;
        }
        CreationCatalog.getInstance().rebuildResolved();
        CreationSyncEvents.syncNow(serverPlayer);
        BodySyncEvents.syncNow(serverPlayer);
        PacketDistributor.sendToPlayer(serverPlayer, new OpenScreenPacket(SCREEN_ID));
    }
}
