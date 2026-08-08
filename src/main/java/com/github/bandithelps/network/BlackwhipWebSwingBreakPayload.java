package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipWebSwingAbility;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: client reached the top of the swing arc — break the whip and release-fling.
 */
public record BlackwhipWebSwingBreakPayload() implements CustomPacketPayload {

    public static final BlackwhipWebSwingBreakPayload INSTANCE = new BlackwhipWebSwingBreakPayload();

    public static final Type<BlackwhipWebSwingBreakPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_web_swing_break"));

    public static final StreamCodec<ByteBuf, BlackwhipWebSwingBreakPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipWebSwingBreakPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlackwhipWebSwingAbility.breakAtMaxArc(player);
            }
        });
    }
}
