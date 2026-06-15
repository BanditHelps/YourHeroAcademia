package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.blackwhip.ClientBlackwhipStruggleState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: tells a restrained player the current state of their struggle minigame.
 */
public record BlackwhipStruggleStatusPayload(boolean active, int taps, int threshold) implements CustomPacketPayload {

    public static final Type<BlackwhipStruggleStatusPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_struggle_status"));

    public static final StreamCodec<ByteBuf, BlackwhipStruggleStatusPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BlackwhipStruggleStatusPayload::active,
            ByteBufCodecs.VAR_INT, BlackwhipStruggleStatusPayload::taps,
            ByteBufCodecs.VAR_INT, BlackwhipStruggleStatusPayload::threshold,
            BlackwhipStruggleStatusPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipStruggleStatusPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBlackwhipStruggleState.set(payload.active(), payload.taps(), payload.threshold()));
    }
}
