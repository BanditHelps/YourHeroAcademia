package com.github.bandithelps.client.dna_analyzer;

import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientDNAAnalyzerState {
    private static final Map<BlockPos, ClientData> STATES = new ConcurrentHashMap<>();

    private ClientDNAAnalyzerState() {
    }

    public static void set(BlockPos pos, boolean analyzed, String sourceName, String sourceUuid, String[] geneSlots) {
        STATES.put(pos, new ClientData(analyzed, sourceName, sourceUuid, geneSlots));
    }

    public static ClientData get(BlockPos pos) {
        return STATES.get(pos);
    }

    public static void clear(BlockPos pos) {
        STATES.remove(pos);
    }

    public record ClientData(boolean analyzed, String sourceName, String sourceUuid, String[] geneSlots) {
    }
}