package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record OpenTreeEditorScreenPayload(String powerId) implements CustomPacketPayload {
    public static final Type<OpenTreeEditorScreenPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "open_tree_editor"));

    public static final StreamCodec<ByteBuf, OpenTreeEditorScreenPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            OpenTreeEditorScreenPayload::powerId,
            OpenTreeEditorScreenPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(OpenTreeEditorScreenPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            try {
                Class<?> openerClass = Class.forName("com.github.bandithelps.client.ClientScreenOpener");
                openerClass.getMethod("openTreeEditorScreen", String.class)
                        .invoke(null, payload.powerId());
            } catch (ClassNotFoundException ignored) {
                // Dedicated server side does not include client classes.
            } catch (ReflectiveOperationException exception) {
                throw new RuntimeException("Failed to open tree editor on client", exception);
            }
        });
    }
}
