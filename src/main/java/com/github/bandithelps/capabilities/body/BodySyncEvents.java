package com.github.bandithelps.capabilities.body;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.BodySyncPayload;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class BodySyncEvents {
    private static final Map<UUID, Integer> LAST_SENT_SIGNATURE = new ConcurrentHashMap<>();

    private BodySyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.level().isClientSide()) {
            return;
        }
        syncIfChanged(player, false);
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            syncIfChanged(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT_SIGNATURE.remove(event.getEntity().getUUID());
    }

    public static void syncNow(ServerPlayer player) {
        syncIfChanged(player, true);
    }

    private static void syncIfChanged(ServerPlayer player, boolean force) {
        IBodyData data = BodyAttachments.get(player);
        CompoundTag payloadTag = new CompoundTag();
        data.saveNBTData(payloadTag);

        int signature = payloadTag.toString().hashCode();
        Integer previous = LAST_SENT_SIGNATURE.get(player.getUUID());
        if (!force && previous != null && previous == signature) {
            return;
        }

        LAST_SENT_SIGNATURE.put(player.getUUID(), signature);
        PacketDistributor.sendToPlayer(player, new BodySyncPayload(payloadTag));
    }
}
