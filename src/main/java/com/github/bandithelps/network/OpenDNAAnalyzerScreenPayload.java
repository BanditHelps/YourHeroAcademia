package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenDNAAnalyzerScreenPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<OpenDNAAnalyzerScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "open_dna_analyzer_screen"));

    public static final StreamCodec<ByteBuf, OpenDNAAnalyzerScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            OpenDNAAnalyzerScreenPayload::blockPos,
            OpenDNAAnalyzerScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenDNAAnalyzerScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> openerClass = Class.forName("com.github.bandithelps.client.ClientScreenOpener");
                openerClass.getMethod("openDNAAnalyzerScreen", BlockPos.class).invoke(null, payload.blockPos());
            } catch (ClassNotFoundException ignored) {
                // Dedicated server side does not include client classes.
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to open DNA analyzer screen on client", exception);
            }
        });
    }
}
