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

import java.util.Arrays;
import java.util.List;

public record DNAAnalyzerExtractPayload(
        BlockPos blockPos,
        int[] slotIndexes
) implements CustomPacketPayload {
    public static final Type<DNAAnalyzerExtractPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "dna_analyzer_extract"));

    private static final StreamCodec<ByteBuf, List<Integer>> INT_LIST_CODEC =
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list());

    private static final StreamCodec<ByteBuf, int[]> INT_ARRAY_CODEC =
            INT_LIST_CODEC.map(
                    list -> list.stream().mapToInt(Integer::intValue).toArray(),
                    array -> Arrays.stream(array).boxed().toList()
            );

    public DNAAnalyzerExtractPayload {
        slotIndexes = slotIndexes == null ? new int[0] : Arrays.copyOf(slotIndexes, slotIndexes.length);
    }

    public static final StreamCodec<ByteBuf, DNAAnalyzerExtractPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            DNAAnalyzerExtractPayload::blockPos,
            INT_ARRAY_CODEC,
            DNAAnalyzerExtractPayload::slotIndexes,
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
                analyzer.extractGenes(player, payload.slotIndexes());
            }
        });
    }
}