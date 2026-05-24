package com.github.bandithelps.client.gene_combiner;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

public final class ClientGeneCombinerState {
    private static final Map<BlockPos, ClientData> STATES = new ConcurrentHashMap<>();
    private static volatile BlockPos latestPos;

    private ClientGeneCombinerState() {
    }

    public static void set(
            BlockPos blockPos,
            boolean processing,
            int processingProgress,
            int processingTotalTicks,
            int[] inputGeneCounts,
            String outputKind,
            int outputGeneCount
    ) {
        STATES.put(blockPos, new ClientData(
                processing,
                processingProgress,
                processingTotalTicks,
                inputGeneCounts == null ? new int[0] : inputGeneCounts,
                outputKind == null ? "empty" : outputKind,
                outputGeneCount
        ));
        latestPos = blockPos;
    }

    public static ClientData get(BlockPos blockPos) {
        return STATES.get(blockPos);
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

    public static BlockPos getLatestPos() {
        return latestPos;
    }

    public record ClientData(
            boolean processing,
            int processingProgress,
            int processingTotalTicks,
            int[] inputGeneCounts,
            String outputKind,
            int outputGeneCount
    ) {
    }
}
