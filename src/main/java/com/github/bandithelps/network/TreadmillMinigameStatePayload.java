package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.movement.ClientTreadmillState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * @param deadlineGameTime server level {@link net.minecraft.world.level.Level#getGameTime()} when the QTE ends; 0 if inactive
 */
public record TreadmillMinigameStatePayload(
        boolean active,
        int packedSequence,
        int sequenceLength,
        int progressIndex,
        long deadlineGameTime
) implements CustomPacketPayload {
    public static final Type<TreadmillMinigameStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "treadmill_minigame_state"));

    public static final StreamCodec<ByteBuf, TreadmillMinigameStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            TreadmillMinigameStatePayload::active,
            ByteBufCodecs.VAR_INT,
            TreadmillMinigameStatePayload::packedSequence,
            ByteBufCodecs.VAR_INT,
            TreadmillMinigameStatePayload::sequenceLength,
            ByteBufCodecs.VAR_INT,
            TreadmillMinigameStatePayload::progressIndex,
            ByteBufCodecs.VAR_LONG,
            TreadmillMinigameStatePayload::deadlineGameTime,
            TreadmillMinigameStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TreadmillMinigameStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientTreadmillState.setMinigameState(
                payload.active(),
                payload.packedSequence(),
                payload.sequenceLength(),
                payload.progressIndex(),
                payload.deadlineGameTime()
        ));
    }
}
