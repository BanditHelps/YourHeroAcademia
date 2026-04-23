package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DNAAnalyzerExtractPayload(
        BlockPos blockPos,
        int count,
        int side
) implements CustomPacketPayload {
    public static final Type<DNAAnalyzerExtractPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "dna_analyzer_extract"));

    public static final StreamCodec<ByteBuf, DNAAnalyzerExtractPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            DNAAnalyzerExtractPayload::blockPos,
            ByteBufCodecs.VAR_INT,
            DNAAnalyzerExtractPayload::count,
            ByteBufCodecs.VAR_INT,
            DNAAnalyzerExtractPayload::side,
            DNAAnalyzerExtractPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DNAAnalyzerExtractPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var level = player.level();
            var be = level.getBlockEntity(payload.blockPos());
            if (be instanceof com.github.bandithelps.blocks.DNAAnalyzerBlockEntity analyzer) {
                analyzer.extractGenes(payload.count(), payload.side(), player);
            }
        });
    }
}