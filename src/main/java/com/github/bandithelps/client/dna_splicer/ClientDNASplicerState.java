package com.github.bandithelps.client.dna_splicer;

import net.minecraft.core.BlockPos;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientDNASplicerState {
    private static final Map<BlockPos, ClientData> STATES = new ConcurrentHashMap<>();

    private ClientDNASplicerState() {
    }

    public static void set(BlockPos pos, String sourceName, String sourceUuid, String[] geneSlots, String[] vialGenes, boolean hasDNA, boolean hasVial) {
        STATES.put(pos, new ClientData(sourceName, sourceUuid, geneSlots, vialGenes, hasDNA, hasVial));
    }

    public static ClientData get(BlockPos pos) {
        return STATES.get(pos);
    }

    public static void clear(BlockPos pos) {
        STATES.remove(pos);
    }

    public record ClientData(String sourceName, String sourceUuid, String[] geneSlots, String[] vialGenes, boolean hasDNA, boolean hasVial) {
    }
}