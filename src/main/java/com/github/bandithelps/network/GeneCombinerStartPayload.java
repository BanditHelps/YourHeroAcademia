package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.blocks.GeneCombinerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GeneCombinerStartPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<GeneCombinerStartPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "gene_combiner_start"));

    public static final StreamCodec<ByteBuf, GeneCombinerStartPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GeneCombinerStartPayload::blockPos,
            GeneCombinerStartPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeneCombinerStartPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var be = player.level().getBlockEntity(payload.blockPos());
            if (!(be instanceof GeneCombinerBlockEntity combiner)) {
                return;
            }
            GeneCombinerBlockEntity.GeneCombinerStartResult result = combiner.tryStart(player);
            if (result == GeneCombinerBlockEntity.GeneCombinerStartResult.STARTED) {
                return;
            }
            player.sendSystemMessage(Component.literal(switch (result) {
                case BUSY -> "Gene Combiner is already running.";
                case OUTPUT_BLOCKED -> "Gene Combiner output slot is blocked.";
                case NO_INPUT -> "Gene Combiner requires gene vials in the input slots.";
                case NO_RECIPE -> "No valid combination recipe matches those genes.";
                case TOO_FAR -> "You are too far from this Gene Combiner.";
                default -> "Unable to start Gene Combiner.";
            }));
        });
    }
}
