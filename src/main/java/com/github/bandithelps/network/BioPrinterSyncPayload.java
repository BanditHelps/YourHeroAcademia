package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.bio_printer.ClientBioPrinterState;
import io.netty.buffer.ByteBuf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BioPrinterSyncPayload(
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
) implements CustomPacketPayload {
    public static final Type<BioPrinterSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "bio_printer_sync"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, String[]> STRING_ARRAY_CODEC =
            STRING_LIST_CODEC.map(
                    list -> list.toArray(new String[0]),
                    Arrays::asList
            );

    private static final StreamCodec<ByteBuf, List<Boolean>> BOOL_LIST_CODEC =
            ByteBufCodecs.BOOL.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, boolean[]> BOOL_ARRAY_CODEC =
            BOOL_LIST_CODEC.map(
                    list -> {
                        boolean[] out = new boolean[list.size()];
                        for (int i = 0; i < list.size(); i++) {
                            out[i] = Boolean.TRUE.equals(list.get(i));
                        }
                        return out;
                    },
                    array -> {
                        List<Boolean> out = new ArrayList<>(array.length);
                        for (boolean value : array) {
                            out.add(value);
                        }
                        return out;
                    }
            );

    public BioPrinterSyncPayload {
        sourceName = sourceName == null ? "" : sourceName;
        sourceUuid = sourceUuid == null ? "" : sourceUuid;
        genomeSlots = genomeSlots == null ? new String[0] : genomeSlots;
        genomeSlotLabels = genomeSlotLabels == null ? new String[0] : genomeSlotLabels;
        genomeSlotTooltips = genomeSlotTooltips == null ? new String[0] : genomeSlotTooltips;
        clearableSlots = clearableSlots == null ? new boolean[0] : clearableSlots;
    }

    public static final StreamCodec<ByteBuf, BioPrinterSyncPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            BioPrinterSyncPayload::blockPos,
            ByteBufCodecs.BOOL,
            BioPrinterSyncPayload::processing,
            ByteBufCodecs.VAR_INT,
            BioPrinterSyncPayload::processingProgress,
            ByteBufCodecs.VAR_INT,
            BioPrinterSyncPayload::processingTotalTicks,
            ByteBufCodecs.BOOL,
            BioPrinterSyncPayload::awaitingInjectorExtraction,
            ByteBufCodecs.STRING_UTF8,
            BioPrinterSyncPayload::sourceName,
            ByteBufCodecs.STRING_UTF8,
            BioPrinterSyncPayload::sourceUuid,
            STRING_ARRAY_CODEC,
            BioPrinterSyncPayload::genomeSlots,
            STRING_ARRAY_CODEC,
            BioPrinterSyncPayload::genomeSlotLabels,
            STRING_ARRAY_CODEC,
            BioPrinterSyncPayload::genomeSlotTooltips,
            BOOL_ARRAY_CODEC,
            BioPrinterSyncPayload::clearableSlots,
            BioPrinterSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BioPrinterSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBioPrinterState.set(
                payload.blockPos(),
                payload.processing(),
                payload.processingProgress(),
                payload.processingTotalTicks(),
                payload.awaitingInjectorExtraction(),
                payload.sourceName(),
                payload.sourceUuid(),
                payload.genomeSlots(),
                payload.genomeSlotLabels(),
                payload.genomeSlotTooltips(),
                payload.clearableSlots()
        ));
    }
}
