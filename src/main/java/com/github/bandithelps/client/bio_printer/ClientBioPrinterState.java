package com.github.bandithelps.client.bio_printer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;

public final class ClientBioPrinterState {
    private static final Map<BlockPos, ClientData> STATES = new ConcurrentHashMap<>();
    private static volatile BlockPos latestPos;

    private ClientBioPrinterState() {
    }

    public static void set(
            BlockPos blockPos,
            boolean processing,
            int processingProgress,
            int processingTotalTicks,
            boolean awaitingInjectorExtraction,
            String sourceName,
            String sourceUuid,
            String[] genomeSlots,
            String[] genomeSlotLabels,
            String[] genomeSlotTooltips,
            boolean[] clearableSlots
    ) {
        STATES.put(blockPos, new ClientData(
                processing,
                processingProgress,
                processingTotalTicks,
                awaitingInjectorExtraction,
                sourceName == null ? "" : sourceName,
                sourceUuid == null ? "" : sourceUuid,
                genomeSlots == null ? new String[0] : genomeSlots,
                genomeSlotLabels == null ? new String[0] : genomeSlotLabels,
                genomeSlotTooltips == null ? new String[0] : genomeSlotTooltips,
                clearableSlots == null ? new boolean[0] : clearableSlots
        ));
        latestPos = blockPos;
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
            boolean awaitingInjectorExtraction,
            String sourceName,
            String sourceUuid,
            String[] genomeSlots,
            String[] genomeSlotLabels,
            String[] genomeSlotTooltips,
            boolean[] clearableSlots
    ) {
    }
}
