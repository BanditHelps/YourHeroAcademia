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

public record BioPrinterPrintPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<BioPrinterPrintPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "bio_printer_print"));

    public static final StreamCodec<ByteBuf, BioPrinterPrintPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            BioPrinterPrintPayload::blockPos,
            BioPrinterPrintPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BioPrinterPrintPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var be = player.level().getBlockEntity(payload.blockPos());
            if (!(be instanceof BioPrinterBlockEntity printer)) {
                return;
            }
            BioPrinterBlockEntity.BioPrinterStartResult result = printer.tryStartPrinting(player);
            if (result == BioPrinterBlockEntity.BioPrinterStartResult.STARTED) {
                return;
            }
            player.sendSystemMessage(Component.literal(switch (result) {
                case BUSY -> "Bio Printer is currently printing.";
                case NO_GENOME -> "Build or import a genome before printing.";
                case RESULT_PENDING_EXTRACTION -> "Extract current print with a DNA Injector first.";
                case TOO_FAR -> "You are too far from this Bio Printer.";
                default -> "Unable to start Bio Printer.";
            }));
        });
    }
}
