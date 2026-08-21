package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.blackwhip.ClientBlackwhipChainLeadState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** S2C: sync whether Lead is toggled on for the local player. */
public record BlackwhipChainLeadPayload(boolean active) implements CustomPacketPayload {

    public static final Type<BlackwhipChainLeadPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_chain_lead"));

    public static final StreamCodec<ByteBuf, BlackwhipChainLeadPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, BlackwhipChainLeadPayload::active,
            BlackwhipChainLeadPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipChainLeadPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBlackwhipChainLeadState.setActive(payload.active()));
    }
}
