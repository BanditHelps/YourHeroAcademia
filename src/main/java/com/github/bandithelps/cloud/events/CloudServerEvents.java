package com.github.bandithelps.cloud.events;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.cloud.CloudSimConfig;
import com.github.bandithelps.cloud.CloudSyncService;
import com.github.bandithelps.cloud.CloudVolumeManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class CloudServerEvents {
    private CloudServerEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int interval = CloudSimConfig.simulationIntervalTicks();
        if (event.getServer().getTickCount() % interval != 0) {
            return;
        }

        for (ServerLevel level : event.getServer().getAllLevels()) {
            CloudVolumeManager manager = CloudVolumeManager.forLevel(level);
            manager.tick();
            CloudSyncService.syncLevel(level, manager);

            if (CloudSimConfig.debugLogging() && event.getServer().getTickCount() % 100 == 0) {
                YourHeroAcademia.LOGGER.info(
                        "[cloud] level={} volumes={} changedCells={} pendingDeltas={} packets={} simMs={}",
                        level.dimension(),
                        manager.metrics().simulatedVolumeCount(),
                        manager.metrics().changedCellCount(),
                        manager.metrics().pendingDeltaCellCount(),
                        manager.metrics().sentPacketCount(),
                        String.format("%.3f", manager.metrics().simulationNanos() / 1_000_000.0D)
                );
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            CloudSyncService.forgetPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            CloudVolumeManager.clearLevel(level);
        }
    }
}
