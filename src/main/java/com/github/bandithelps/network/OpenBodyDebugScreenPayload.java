package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenBodyDebugScreenPayload() implements CustomPacketPayload {
    public static final OpenBodyDebugScreenPayload INSTANCE = new OpenBodyDebugScreenPayload();
    public static final Type<OpenBodyDebugScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "open_body_debug_screen"));
    public static final StreamCodec<ByteBuf, OpenBodyDebugScreenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenBodyDebugScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> openerClass = Class.forName("com.github.bandithelps.client.ClientScreenOpener");
                openerClass.getMethod("openBodyDebugScreen").invoke(null);
            } catch (ClassNotFoundException ignored) {
                // Dedicated server side does not include client classes.
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to open body debug screen on client", exception);
            }
        });
    }
}
