package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives Blackwhip tag housekeeping (TTL/distance expiry, count sync) and clears state when a player
 * leaves. Replaces the legacy {@code blackwhip_auto_refresh} ability with a simple server tick.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class BlackwhipServerEvents {

    private BlackwhipServerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        BlackwhipStruggle.cleanup(event.getServer());
        if (event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            BlackwhipTagStore.tick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlackwhipTagStore.clearTags(player);
        }
    }
}
