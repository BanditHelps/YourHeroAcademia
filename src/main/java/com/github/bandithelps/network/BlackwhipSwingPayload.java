package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.blackwhip.ClientBlackwhipSwingState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: tells the owning client to start (or stop) a Spider-Man pendulum swing on the given anchor.
 */
public record BlackwhipSwingPayload(boolean active, double anchorX, double anchorY, double anchorZ, float ropeLength)
        implements CustomPacketPayload {

    public static final Type<BlackwhipSwingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_swing"));

    public static final StreamCodec<ByteBuf, BlackwhipSwingPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BlackwhipSwingPayload::active,
            ByteBufCodecs.DOUBLE, BlackwhipSwingPayload::anchorX,
            ByteBufCodecs.DOUBLE, BlackwhipSwingPayload::anchorY,
            ByteBufCodecs.DOUBLE, BlackwhipSwingPayload::anchorZ,
            ByteBufCodecs.FLOAT, BlackwhipSwingPayload::ropeLength,
            BlackwhipSwingPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipSwingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBlackwhipSwingState.set(
                payload.active(), payload.anchorX(), payload.anchorY(), payload.anchorZ(), payload.ropeLength()));
    }
}
