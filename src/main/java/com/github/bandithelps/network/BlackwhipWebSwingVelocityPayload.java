package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipWebSwingAbility;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: syncs the owning client's swing velocity so release fling uses real pendulum momentum.
 */
public record BlackwhipWebSwingVelocityPayload(double vx, double vy, double vz) implements CustomPacketPayload {

    public static final Type<BlackwhipWebSwingVelocityPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_web_swing_vel"));

    public static final StreamCodec<ByteBuf, BlackwhipWebSwingVelocityPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public BlackwhipWebSwingVelocityPayload decode(ByteBuf buf) {
            return new BlackwhipWebSwingVelocityPayload(buf.readDouble(), buf.readDouble(), buf.readDouble());
        }

        @Override
        public void encode(ByteBuf buf, BlackwhipWebSwingVelocityPayload payload) {
            buf.writeDouble(payload.vx());
            buf.writeDouble(payload.vy());
            buf.writeDouble(payload.vz());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipWebSwingVelocityPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlackwhipWebSwingAbility.acceptClientVelocity(player, new Vec3(payload.vx(), payload.vy(), payload.vz()));
            }
        });
    }
}
