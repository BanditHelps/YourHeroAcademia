package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.blackwhip.ClientBlackwhipChainZipAnimState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: drives Blackwhip chain zip player animations on the owning client.
 * <ul>
 *   <li>0 = stop / none</li>
 *   <li>1 = reel wind-up ({@code yha:reel_in})</li>
 *   <li>2 = punch on contact ({@code yha:flying_punch})</li>
 * </ul>
 */
public record BlackwhipChainZipAnimPayload(byte phase) implements CustomPacketPayload {

    public static final byte PHASE_NONE = 0;
    public static final byte PHASE_REEL = 1;
    public static final byte PHASE_PUNCH = 2;

    public static final Type<BlackwhipChainZipAnimPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_chain_zip_anim"));

    public static final StreamCodec<ByteBuf, BlackwhipChainZipAnimPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE, BlackwhipChainZipAnimPayload::phase,
            BlackwhipChainZipAnimPayload::new
    );

    public static BlackwhipChainZipAnimPayload none() {
        return new BlackwhipChainZipAnimPayload(PHASE_NONE);
    }

    public static BlackwhipChainZipAnimPayload reel() {
        return new BlackwhipChainZipAnimPayload(PHASE_REEL);
    }

    public static BlackwhipChainZipAnimPayload punch() {
        return new BlackwhipChainZipAnimPayload(PHASE_PUNCH);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipChainZipAnimPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientBlackwhipChainZipAnimState.Phase phase = switch (payload.phase()) {
                case PHASE_REEL -> ClientBlackwhipChainZipAnimState.Phase.REEL;
                case PHASE_PUNCH -> ClientBlackwhipChainZipAnimState.Phase.PUNCH;
                default -> ClientBlackwhipChainZipAnimState.Phase.NONE;
            };
            ClientBlackwhipChainZipAnimState.setPhase(phase);
        });
    }
}
