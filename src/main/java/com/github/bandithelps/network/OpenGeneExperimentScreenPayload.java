package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenGeneExperimentScreenPayload() implements CustomPacketPayload {
    public static final OpenGeneExperimentScreenPayload INSTANCE = new OpenGeneExperimentScreenPayload();
    public static final Type<OpenGeneExperimentScreenPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "open_gene_screen"));
    public static final StreamCodec<ByteBuf, OpenGeneExperimentScreenPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenGeneExperimentScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> clientScreenOpenerClass = Class.forName("com.github.bandithelps.client.ClientScreenOpener");
                clientScreenOpenerClass.getMethod("openGeneScreen").invoke(null);
            } catch (ClassNotFoundException ignored) {
                // Dedicated server side does not include client classes.
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to open YHA test screen on client", exception);
            }
        });
    }
}
