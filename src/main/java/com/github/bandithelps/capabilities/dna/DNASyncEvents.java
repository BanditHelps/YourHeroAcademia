package com.github.bandithelps.capabilities.dna;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.gene.DNA;
import com.github.bandithelps.network.DNASyncPayload;
import com.github.bandithelps.utils.gene.GeneUtil;
import net.minecraft.nbt.CompoundTag;
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
    private static final String PERSISTED_TAG = "PlayerPersisted";
    private static final String INITIAL_PLAYER_DNA_TAG = "yha.initial_player_dna";

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
        CompoundTag persistentData = player.getPersistentData();
        CompoundTag persisted = persistentData.getCompound(PERSISTED_TAG).orElseGet(CompoundTag::new);
        if (persisted.getBoolean(INITIAL_PLAYER_DNA_TAG).orElse(false)) {
            return;
        }

        IDNAData dnaData = DNAAttachments.get(player);
        if (!dnaData.hasDNA()) {
            DNA startingDNA = GeneUtil.generateInitialPlayerDNA(player.level().getSeed(), player.getUUID(), player.getName().getString());
            dnaData.setDNA(GeneUtil.serializeDNA(startingDNA));
            dnaData.setDNAFatigued(false);
        }

        persisted.putBoolean(INITIAL_PLAYER_DNA_TAG, true);
        persistentData.put(PERSISTED_TAG, persisted);
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