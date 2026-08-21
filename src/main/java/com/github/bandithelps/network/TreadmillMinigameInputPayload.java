package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.blocks.TreadmillBlockEvents;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TreadmillMinigameInputPayload(int keyIndex) implements CustomPacketPayload {
    public static final Type<TreadmillMinigameInputPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "treadmill_minigame_input"));

    public static final StreamCodec<ByteBuf, TreadmillMinigameInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            TreadmillMinigameInputPayload::keyIndex,
            TreadmillMinigameInputPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TreadmillMinigameInputPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            TreadmillBlockEvents.handleMinigameInput(player, payload.keyIndex());
        });
    }
}
