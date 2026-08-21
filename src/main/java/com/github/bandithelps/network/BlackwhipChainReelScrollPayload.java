package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.blackwhip.chain.BlackwhipMoveTaggedAbility;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S: one mouse-scroll notch while Puppet is held (Lead required on server).
 * Positive direction = retract (shorten); negative = extend.
 */
public record BlackwhipChainReelScrollPayload(int direction) implements CustomPacketPayload {

    public static final Type<BlackwhipChainReelScrollPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "blackwhip_chain_reel_scroll"));

    public static final StreamCodec<ByteBuf, BlackwhipChainReelScrollPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BlackwhipChainReelScrollPayload::direction,
            BlackwhipChainReelScrollPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlackwhipChainReelScrollPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                BlackwhipMoveTaggedAbility.handleScroll(player, payload.direction());
            }
        });
    }
}
