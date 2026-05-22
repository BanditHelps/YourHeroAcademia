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

public record DNAAnalyzerRenamePayload(
        BlockPos blockPos,
        int slotIndex,
        String name
) implements CustomPacketPayload {
    public static final Type<DNAAnalyzerRenamePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "dna_analyzer_rename"));

    public static final StreamCodec<ByteBuf, DNAAnalyzerRenamePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            DNAAnalyzerRenamePayload::blockPos,
            ByteBufCodecs.VAR_INT,
            DNAAnalyzerRenamePayload::slotIndex,
            ByteBufCodecs.STRING_UTF8,
            DNAAnalyzerRenamePayload::name,
            DNAAnalyzerRenamePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DNAAnalyzerRenamePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var level = player.level();
            var be = level.getBlockEntity(payload.blockPos());
            if (be instanceof com.github.bandithelps.blocks.DNAAnalyzerBlockEntity analyzer) {
                analyzer.renameGene(payload.slotIndex(), payload.name(), player);
            }
        });
    }
}
