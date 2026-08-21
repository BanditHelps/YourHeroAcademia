package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.blackwhip.ClientBlackwhipChargeZipState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: drive Charge Zip pullback and charge HUD on the owning client.
 * Yaw/pitch are the launch facing from charge start, not a camera lock.
 */
public record BlackwhipChainChargeZipPayload(
        boolean active,
        float yaw,
        float pitch,
        float chargeRatio,
        float pullbackSpeed
) implements CustomPacketPayload {

    public static final Type<BlackwhipChainChargeZipPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_chain_charge_zip"));

    public static final StreamCodec<ByteBuf, BlackwhipChainChargeZipPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlackwhipChainChargeZipPayload decode(ByteBuf buf) {
            boolean active = buf.readBoolean();
            float yaw = buf.readFloat();
            float pitch = buf.readFloat();
            float charge = buf.readFloat();
            float pullback = buf.readFloat();
            return new BlackwhipChainChargeZipPayload(active, yaw, pitch, charge, pullback);
        }

        @Override
        public void encode(ByteBuf buf, BlackwhipChainChargeZipPayload payload) {
            buf.writeBoolean(payload.active());
            buf.writeFloat(payload.yaw());
            buf.writeFloat(payload.pitch());
            buf.writeFloat(payload.chargeRatio());
            buf.writeFloat(payload.pullbackSpeed());
        }
    };

    public static BlackwhipChainChargeZipPayload stop() {
        return new BlackwhipChainChargeZipPayload(false, 0.0f, 0.0f, 0.0f, 0.0f);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipChainChargeZipPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBlackwhipChargeZipState.apply(payload));
    }
}
