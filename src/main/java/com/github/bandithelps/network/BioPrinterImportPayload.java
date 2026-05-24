package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.blocks.BioPrinterBlockEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BioPrinterImportPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<BioPrinterImportPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "bio_printer_import"));

    public static final StreamCodec<ByteBuf, BioPrinterImportPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            BioPrinterImportPayload::blockPos,
            BioPrinterImportPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BioPrinterImportPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var be = player.level().getBlockEntity(payload.blockPos());
            if (!(be instanceof BioPrinterBlockEntity printer)) {
                return;
            }
            if (!printer.importFromPlayerDNA(player)) {
                player.sendSystemMessage(Component.literal("No DNA found to import."));
            }
        });
    }
}
