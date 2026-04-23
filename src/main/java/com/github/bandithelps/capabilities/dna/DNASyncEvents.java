package com.github.bandithelps.capabilities.dna;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.DNASyncPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class DNASyncEvents {
    private static final Map<UUID, Snapshot> LAST_SENT = new ConcurrentHashMap<>();

    private DNASyncEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            initializePlayerDNA(player);
            syncIfChanged(player, true);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_SENT.remove(event.getEntity().getUUID());
    }

    public static void syncNow(ServerPlayer player) {
        syncIfChanged(player, true);
    }

    private static void initializePlayerDNA(ServerPlayer player) {
    }

    private static void syncIfChanged(ServerPlayer player, boolean force) {
        IDNAData dna = DNAAttachments.get(player);
        Snapshot next = new Snapshot(
                dna.getDNA(),
                dna.hasDNA(),
                dna.getIntelligence(),
                dna.isDNAFatigued()
        );
        Snapshot previous = LAST_SENT.get(player.getUUID());
        if (!force && next.equals(previous)) {
            return;
        }

        LAST_SENT.put(player.getUUID(), next);
        PacketDistributor.sendToPlayer(player, new DNASyncPayload(
                next.dna(),
                next.hasDNA(),
                next.intelligence(),
                next.dnaFatigued()
        ));
    }

    private record Snapshot(
            String dna,
            boolean hasDNA,
            int intelligence,
            boolean dnaFatigued
    ) {
    }
}