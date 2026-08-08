package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.blackwhip.ClientBlackwhipWebSwingState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * S2C: start/stop PS5-style web swing on the owning client, with physics tunables.
 */
public record BlackwhipWebSwingPayload(
        boolean active,
        double anchorX,
        double anchorY,
        double anchorZ,
        float ropeLength,
        float minRope,
        float maxRope,
        float pumpAccel,
        float turnAssist,
        float autoReelRate,
        float damping,
        float maxSpeed,
        float brakeDamp
) implements CustomPacketPayload {

    public static final Type<BlackwhipWebSwingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_web_swing"));

    public static final StreamCodec<ByteBuf, BlackwhipWebSwingPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlackwhipWebSwingPayload decode(ByteBuf buf) {
            boolean active = buf.readBoolean();
            double ax = buf.readDouble();
            double ay = buf.readDouble();
            double az = buf.readDouble();
            float rope = buf.readFloat();
            float minRope = buf.readFloat();
            float maxRope = buf.readFloat();
            float pump = buf.readFloat();
            float turn = buf.readFloat();
            float autoReel = buf.readFloat();
            float damp = buf.readFloat();
            float maxSpeed = buf.readFloat();
            float brake = buf.readFloat();
            return new BlackwhipWebSwingPayload(
                    active, ax, ay, az, rope, minRope, maxRope, pump, turn, autoReel, damp, maxSpeed, brake);
        }

        @Override
        public void encode(ByteBuf buf, BlackwhipWebSwingPayload payload) {
            buf.writeBoolean(payload.active());
            buf.writeDouble(payload.anchorX());
            buf.writeDouble(payload.anchorY());
            buf.writeDouble(payload.anchorZ());
            buf.writeFloat(payload.ropeLength());
            buf.writeFloat(payload.minRope());
            buf.writeFloat(payload.maxRope());
            buf.writeFloat(payload.pumpAccel());
            buf.writeFloat(payload.turnAssist());
            buf.writeFloat(payload.autoReelRate());
            buf.writeFloat(payload.damping());
            buf.writeFloat(payload.maxSpeed());
            buf.writeFloat(payload.brakeDamp());
        }
    };

    public static BlackwhipWebSwingPayload stop() {
        return new BlackwhipWebSwingPayload(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipWebSwingPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBlackwhipWebSwingState.apply(payload));
    }
}
