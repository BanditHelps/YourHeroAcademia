package com.github.bandithelps.gene;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.GeneAliasSyncPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class GeneAliasSyncEvents {
    private GeneAliasSyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncAllToPlayer(player);
        }
    }

    public static void syncAllToPlayer(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        GeneAliasSavedData data = GeneAliasSavedData.get(serverLevel);
        PacketDistributor.sendToPlayer(player, GeneAliasSyncPayload.full(data.getAliasesView()));
    }

    public static void broadcastAliasUpdate(ServerLevel level, String sourceUuid, String geneTypeId, String alias) {
        if (level == null || sourceUuid == null || sourceUuid.isBlank() || geneTypeId == null || geneTypeId.isBlank()) {
            return;
        }
        String key = sourceUuid + "|" + geneTypeId;
        String value = alias == null ? "" : alias.trim();
        GeneAliasSyncPayload payload = GeneAliasSyncPayload.singleUpdate(key, value);
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }
}
