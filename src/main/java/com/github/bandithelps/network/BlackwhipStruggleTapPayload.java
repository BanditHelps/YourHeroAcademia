package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.utils.blackwhip.BlackwhipStruggle;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: a single "tap" from a restrained player trying to break free of a Blackwhip restraint.
 */
public record BlackwhipStruggleTapPayload() implements CustomPacketPayload {

    public static final BlackwhipStruggleTapPayload INSTANCE = new BlackwhipStruggleTapPayload();

    public static final Type<BlackwhipStruggleTapPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_struggle_tap"));

    public static final StreamCodec<ByteBuf, BlackwhipStruggleTapPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipStruggleTapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlackwhipStruggle.tap(player);
            }
        });
    }
}
