package com.github.bandithelps.capabilities.loadout;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.AbilityLoadoutSyncPayload;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class AbilityLoadoutSyncEvents {
    private static final Map<UUID, Snapshot> LAST_SENT = new ConcurrentHashMap<>();

    private AbilityLoadoutSyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncNow(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    public static void syncNow(ServerPlayer player) {
        AbilityLoadoutData loadout = AbilityLoadoutAttachments.get(player);
        Snapshot next = new Snapshot(loadout.encodedSlots().toString(), loadout.encodedModes().toString());
        LAST_SENT.put(player.getUUID(), next);
        PacketDistributor.sendToPlayer(player, new AbilityLoadoutSyncPayload(
                loadout.encodedSlots(),
                loadout.encodedModes()
        ));
    }

    private record Snapshot(String slots, String modes) {
    }
}
