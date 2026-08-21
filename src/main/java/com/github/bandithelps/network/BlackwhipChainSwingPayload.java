package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.blackwhip.ClientBlackwhipChainSwingState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: start/stop chain Blackwhip pendulum swing on the owning client, with physics tunables.
 */
public record BlackwhipChainSwingPayload(
        boolean active,
        double anchorX,
        double anchorY,
        double anchorZ,
        float ropeLength,
        float minRope,
        float maxRope,
        float reelSpeed,
        float pumpAccel,
        float damping,
        float maxSpeed
) implements CustomPacketPayload {

    public static final Type<BlackwhipChainSwingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_chain_swing"));

    public static final StreamCodec<ByteBuf, BlackwhipChainSwingPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlackwhipChainSwingPayload decode(ByteBuf buf) {
            boolean active = buf.readBoolean();
            double ax = buf.readDouble();
            double ay = buf.readDouble();
            double az = buf.readDouble();
            float rope = buf.readFloat();
            float minRope = buf.readFloat();
            float maxRope = buf.readFloat();
            float reel = buf.readFloat();
            float pump = buf.readFloat();
            float damp = buf.readFloat();
            float maxSpeed = buf.readFloat();
            return new BlackwhipChainSwingPayload(active, ax, ay, az, rope, minRope, maxRope, reel, pump, damp, maxSpeed);
        }

        @Override
        public void encode(ByteBuf buf, BlackwhipChainSwingPayload payload) {
            buf.writeBoolean(payload.active());
            buf.writeDouble(payload.anchorX());
            buf.writeDouble(payload.anchorY());
            buf.writeDouble(payload.anchorZ());
            buf.writeFloat(payload.ropeLength());
            buf.writeFloat(payload.minRope());
            buf.writeFloat(payload.maxRope());
            buf.writeFloat(payload.reelSpeed());
            buf.writeFloat(payload.pumpAccel());
            buf.writeFloat(payload.damping());
            buf.writeFloat(payload.maxSpeed());
        }
    };

    public static BlackwhipChainSwingPayload stop() {
        return new BlackwhipChainSwingPayload(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipChainSwingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBlackwhipChainSwingState.apply(payload));
    }
}
