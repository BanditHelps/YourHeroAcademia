package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.blocks.GeneCombinerBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record GeneCombinerTransferPayload(BlockPos blockPos, int inventorySlot, int inputSlot) implements CustomPacketPayload {
    public static final Type<GeneCombinerTransferPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "gene_combiner_transfer"));

    public static final StreamCodec<ByteBuf, GeneCombinerTransferPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GeneCombinerTransferPayload::blockPos,
            ByteBufCodecs.VAR_INT,
            GeneCombinerTransferPayload::inventorySlot,
            ByteBufCodecs.VAR_INT,
            GeneCombinerTransferPayload::inputSlot,
            GeneCombinerTransferPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeneCombinerTransferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var be = player.level().getBlockEntity(payload.blockPos());
            if (!(be instanceof GeneCombinerBlockEntity combiner)) {
                return;
            }
            if (payload.inventorySlot() >= 0) {
                combiner.transferInventoryVialToInput(player, payload.inventorySlot(), payload.inputSlot());
            } else {
                combiner.clearInputSlot(player, payload.inputSlot());
            }
        });
    }
}
