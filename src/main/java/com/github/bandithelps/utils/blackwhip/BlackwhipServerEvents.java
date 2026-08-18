package com.github.bandithelps.utils.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipChainChargeZipAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipChainZipAbility;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipWebSwingAbility;
import com.github.bandithelps.entities.BlackwhipChainEntity;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Drives Blackwhip tag housekeeping (TTL/distance expiry, count sync) and clears state when a player
 * leaves. Also runs chain IK after owner movement settles each tick.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class BlackwhipServerEvents {

    private BlackwhipServerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        BlackwhipStruggle.cleanup(event.getServer());

        BlackwhipBlockTossStore.tick(event.getServer());

        // Chain IK after entity movement so wrist attach tracks the owner without a tick of lag.
        for (BlackwhipChainEntity chain : BlackwhipChainEntity.activeServerChains()) {
            if (chain.isAlive()) {
                chain.serverPostTick();
            }
        }

        BlackwhipWebSwingAbility.tickReleaseEchoes(event.getServer());
        BlackwhipChainZipAbility.tickSessions(event.getServer());
        BlackwhipChainChargeZipAbility.tickFlightHits(event.getServer());

        if (event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers()) {
            BlackwhipTagStore.tick(player);
            BlackwhipChainTagStore.tick(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlackwhipTagStore.clearTags(player);
            BlackwhipChainTagStore.clearTags(player);
            BlackwhipBlockTossStore.dropAll(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlackwhipBlockTossStore.dropAll(player);
        }
    }
}
