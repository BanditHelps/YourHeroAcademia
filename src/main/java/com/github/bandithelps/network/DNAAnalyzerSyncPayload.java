package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.dna_analyzer.ClientDNAAnalyzerState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Arrays;
import java.util.List;

public record DNAAnalyzerSyncPayload(
        BlockPos blockPos,
        boolean analyzed,
        String sourceName,
        String sourceUuid,
        String[] geneSlots
) implements CustomPacketPayload {
    public static final Type<DNAAnalyzerSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "dna_analyzer_sync"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, String[]> STRING_ARRAY_CODEC =
            STRING_LIST_CODEC.map(
                    list -> list.toArray(new String[0]),
                    Arrays::asList
            );

    public static final StreamCodec<ByteBuf, DNAAnalyzerSyncPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            DNAAnalyzerSyncPayload::blockPos,
            ByteBufCodecs.BOOL,
            DNAAnalyzerSyncPayload::analyzed,
            ByteBufCodecs.STRING_UTF8,
            DNAAnalyzerSyncPayload::sourceName,
            ByteBufCodecs.STRING_UTF8,
            DNAAnalyzerSyncPayload::sourceUuid,
            STRING_ARRAY_CODEC,
            DNAAnalyzerSyncPayload::geneSlots,
            DNAAnalyzerSyncPayload::new
    );

    public String[] geneSlots() {
        return this.geneSlots;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DNAAnalyzerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDNAAnalyzerState.set(
                payload.blockPos(),
                payload.analyzed(),
                payload.sourceName(),
                payload.sourceUuid(),
                payload.geneSlots()
        ));
    }
}