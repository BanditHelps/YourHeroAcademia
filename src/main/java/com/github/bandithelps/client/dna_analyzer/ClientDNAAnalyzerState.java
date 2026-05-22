package com.github.bandithelps.client.dna_analyzer;

import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientDNAAnalyzerState {
    private static final Map<BlockPos, ClientData> STATES = new ConcurrentHashMap<>();
    private static volatile BlockPos latestPos;

    private ClientDNAAnalyzerState() {
    }

    public static void set(BlockPos pos, boolean analyzed, String sourceName, String sourceUuid, String[] geneSlots) {
        STATES.put(pos, new ClientData(analyzed, sourceName, sourceUuid, geneSlots));
        latestPos = pos;
    }

    public static ClientData get(BlockPos pos) {
        return STATES.get(pos);
    }

    public static void clear(BlockPos pos) {
        STATES.remove(pos);
        if (pos.equals(latestPos)) {
            latestPos = null;
        }
    }

    public static ClientData getLatest() {
        if (latestPos != null) {
            ClientData data = STATES.get(latestPos);
            if (data != null) {
                return data;
            }
        }
        return STATES.values().stream().findFirst().orElse(null);
    }

    public record ClientData(boolean analyzed, String sourceName, String sourceUuid, String[] geneSlots) {
    }
}