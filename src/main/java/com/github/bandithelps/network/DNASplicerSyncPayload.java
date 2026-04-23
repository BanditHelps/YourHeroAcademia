package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.dna_splicer.ClientDNASplicerState;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import java.util.Arrays;
import java.util.List;

public record DNASplicerSyncPayload(
        BlockPos blockPos,
        String sourceName,
        String sourceUuid,
        String[] geneSlots,
        String[] vialGenes
) implements CustomPacketPayload {
    public static final Type<DNASplicerSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "dna_splicer_sync"));

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST_CODEC =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, String[]> STRING_ARRAY_CODEC =
            STRING_LIST_CODEC.map(
                    list -> list.toArray(new String[0]),
                    Arrays::asList
            );

    public static final StreamCodec<ByteBuf, DNASplicerSyncPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            DNASplicerSyncPayload::blockPos,
            ByteBufCodecs.STRING_UTF8,
            DNASplicerSyncPayload::sourceName,
            ByteBufCodecs.STRING_UTF8,
            DNASplicerSyncPayload::sourceUuid,
            STRING_ARRAY_CODEC,
            DNASplicerSyncPayload::geneSlots,
            STRING_ARRAY_CODEC,
            DNASplicerSyncPayload::vialGenes,
            DNASplicerSyncPayload::new
    );

    public String[] geneSlots() {
        return this.geneSlots;
    }

    public String[] vialGenes() {
        return this.vialGenes;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DNASplicerSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDNASplicerState.set(
                payload.blockPos(),
                payload.sourceName(),
                payload.sourceUuid(),
                payload.geneSlots(),
                payload.vialGenes(),
                true,
                true
        ));
    }
}