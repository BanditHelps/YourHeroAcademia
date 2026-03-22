package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.stamina.ClientStaminaState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StaminaDebugOverlayPayload(boolean enabled) implements CustomPacketPayload {
    public static final Type<StaminaDebugOverlayPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "stamina_debug_overlay"));

    public static final StreamCodec<ByteBuf, StaminaDebugOverlayPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            StaminaDebugOverlayPayload::enabled,
            StaminaDebugOverlayPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StaminaDebugOverlayPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientStaminaState.setDebugOverlayEnabled(payload.enabled()));
    }
}
