package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.blackwhip.ClientBlackwhipChainReelState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: start/stop a Puppet hold session so the client can capture mouse scroll for reeling. */
public record BlackwhipChainReelSessionPayload(boolean active, String mode) implements CustomPacketPayload {

    public static final Type<BlackwhipChainReelSessionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_chain_reel_session"));

    public static final StreamCodec<ByteBuf, BlackwhipChainReelSessionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BlackwhipChainReelSessionPayload::active,
            ByteBufCodecs.STRING_UTF8, BlackwhipChainReelSessionPayload::mode,
            BlackwhipChainReelSessionPayload::new
    );

    public static BlackwhipChainReelSessionPayload start(String mode) {
        return new BlackwhipChainReelSessionPayload(true, mode == null ? "all" : mode);
    }

    public static BlackwhipChainReelSessionPayload stop() {
        return new BlackwhipChainReelSessionPayload(false, "");
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipChainReelSessionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (payload.active()) {
                ClientBlackwhipChainReelState.start(payload.mode());
            } else {
                ClientBlackwhipChainReelState.stop();
            }
        });
    }
}
