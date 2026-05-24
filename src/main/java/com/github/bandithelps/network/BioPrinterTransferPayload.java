package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.blocks.BioPrinterBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BioPrinterTransferPayload(BlockPos blockPos, int inventorySlot, int targetSlot) implements CustomPacketPayload {
    public static final Type<BioPrinterTransferPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "bio_printer_transfer"));

    public static final StreamCodec<ByteBuf, BioPrinterTransferPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            BioPrinterTransferPayload::blockPos,
            ByteBufCodecs.VAR_INT,
            BioPrinterTransferPayload::inventorySlot,
            ByteBufCodecs.VAR_INT,
            BioPrinterTransferPayload::targetSlot,
            BioPrinterTransferPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BioPrinterTransferPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var be = player.level().getBlockEntity(payload.blockPos());
            if (!(be instanceof BioPrinterBlockEntity printer)) {
                return;
            }
            if (payload.inventorySlot() >= 0) {
                printer.transferInventoryVialToGenome(player, payload.inventorySlot(), payload.targetSlot());
            } else {
                printer.clearGenomeSlot(player, payload.targetSlot());
            }
        });
    }
}
