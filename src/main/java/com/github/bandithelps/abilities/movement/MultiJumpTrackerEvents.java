package com.github.bandithelps.abilities.movement;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class MultiJumpTrackerEvents {

    private MultiJumpTrackerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }

        if (player.onGround()) {
            MultiJumpAbility.resetJumps(player);
        }
    }
}
