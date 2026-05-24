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

public record GeneCombinerSlotActionPayload(BlockPos blockPos, int slotIndex) implements CustomPacketPayload {
    public static final Type<GeneCombinerSlotActionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "gene_combiner_slot_action"));

    public static final StreamCodec<ByteBuf, GeneCombinerSlotActionPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            GeneCombinerSlotActionPayload::blockPos,
            ByteBufCodecs.VAR_INT,
            GeneCombinerSlotActionPayload::slotIndex,
            GeneCombinerSlotActionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(GeneCombinerSlotActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var be = player.level().getBlockEntity(payload.blockPos());
            if (be instanceof GeneCombinerBlockEntity combiner) {
                combiner.handleSlotInteraction(player, payload.slotIndex());
            }
        });
    }
}
