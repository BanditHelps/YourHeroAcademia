package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenGeneCombinerScreenPayload(BlockPos blockPos) implements CustomPacketPayload {
    public static final Type<OpenGeneCombinerScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "open_gene_combiner_screen"));

    public static final StreamCodec<ByteBuf, OpenGeneCombinerScreenPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            OpenGeneCombinerScreenPayload::blockPos,
            OpenGeneCombinerScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenGeneCombinerScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> openerClass = Class.forName("com.github.bandithelps.client.ClientScreenOpener");
                openerClass.getMethod("openGeneCombinerScreen", BlockPos.class).invoke(null, payload.blockPos());
            } catch (ClassNotFoundException ignored) {
                // Dedicated server side does not include client classes.
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to open gene combiner screen on client", exception);
            }
        });
    }
}
