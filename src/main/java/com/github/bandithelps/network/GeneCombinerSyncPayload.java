package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.gene_combiner.ClientGeneCombinerState;
import io.netty.buffer.ByteBuf;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GeneCombinerSyncPayload(
        BlockPos blockPos,
        boolean processing,
        int processingProgress,
        int processingTotalTicks,
        int[] inputGeneCounts,
        String[] inputSlotLabels,
        String[] inputSlotTooltips,
        String outputKind,
        int outputGeneCount,
        String outputLabel
) implements CustomPacketPayload {
    public static final Type<GeneCombinerSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "gene_combiner_sync"));

    private static final StreamCodec<ByteBuf, List<Integer>> INT_LIST_CODEC =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, int[]> INT_ARRAY_CODEC =
            INT_LIST_CODEC.map(
                    list -> list.stream().mapToInt(Integer::intValue).toArray(),
                    array -> Arrays.stream(array).boxed().toList()
            );

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, String[]> STRING_ARRAY_CODEC =
            STRING_LIST_CODEC.map(
                    list -> list.toArray(new String[0]),
                    Arrays::asList
            );

    public GeneCombinerSyncPayload {
        if (inputGeneCounts == null) {
            inputGeneCounts = new int[0];
        }
        if (inputSlotLabels == null) {
            inputSlotLabels = new String[0];
        }
        if (inputSlotTooltips == null) {
            inputSlotTooltips = new String[0];
        }
        outputKind = outputKind == null ? "empty" : outputKind;
        outputLabel = outputLabel == null ? "" : outputLabel;
    }

    public static final StreamCodec<ByteBuf, GeneCombinerSyncPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GeneCombinerSyncPayload::blockPos,
            ByteBufCodecs.BOOL,
            GeneCombinerSyncPayload::processing,
            ByteBufCodecs.VAR_INT,
            GeneCombinerSyncPayload::processingProgress,
            ByteBufCodecs.VAR_INT,
            GeneCombinerSyncPayload::processingTotalTicks,
            INT_ARRAY_CODEC,
            GeneCombinerSyncPayload::inputGeneCounts,
            STRING_ARRAY_CODEC,
            GeneCombinerSyncPayload::inputSlotLabels,
            STRING_ARRAY_CODEC,
            GeneCombinerSyncPayload::inputSlotTooltips,
            ByteBufCodecs.STRING_UTF8,
            GeneCombinerSyncPayload::outputKind,
            ByteBufCodecs.VAR_INT,
            GeneCombinerSyncPayload::outputGeneCount,
            ByteBufCodecs.STRING_UTF8,
            GeneCombinerSyncPayload::outputLabel,
            GeneCombinerSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeneCombinerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientGeneCombinerState.set(
                payload.blockPos(),
                payload.processing(),
                payload.processingProgress(),
                payload.processingTotalTicks(),
                payload.inputGeneCounts(),
                payload.inputSlotLabels(),
                payload.inputSlotTooltips(),
                payload.outputKind(),
                payload.outputGeneCount(),
                payload.outputLabel()
        ));
    }
}
