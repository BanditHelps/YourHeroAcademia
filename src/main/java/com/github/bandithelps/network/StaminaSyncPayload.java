package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.stamina.ClientStaminaState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record StaminaSyncPayload(
        int currentStamina,
        int maxStamina,
        int usageTotal,
        int regenCooldown,
        int regenAmount,
        int exhaustionLevel,
        boolean lastHurrahUsed,
        boolean powersDisabled,
        boolean initialized,
        int upgradePoints,
        int pointsProgress
) implements CustomPacketPayload {
    public static final Type<StaminaSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "stamina_sync"));

    public static final StreamCodec<ByteBuf, StaminaSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            StaminaSyncPayload::currentStamina,
            ByteBufCodecs.VAR_INT,
            StaminaSyncPayload::maxStamina,
            ByteBufCodecs.VAR_INT,
            StaminaSyncPayload::usageTotal,
            ByteBufCodecs.VAR_INT,
            StaminaSyncPayload::regenCooldown,
            ByteBufCodecs.VAR_INT,
            StaminaSyncPayload::regenAmount,
            ByteBufCodecs.VAR_INT,
            StaminaSyncPayload::exhaustionLevel,
            ByteBufCodecs.BOOL,
            StaminaSyncPayload::lastHurrahUsed,
            ByteBufCodecs.BOOL,
            StaminaSyncPayload::powersDisabled,
            ByteBufCodecs.BOOL,
            StaminaSyncPayload::initialized,
            ByteBufCodecs.VAR_INT,
            StaminaSyncPayload::upgradePoints,
            ByteBufCodecs.VAR_INT,
            StaminaSyncPayload::pointsProgress,
            StaminaSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StaminaSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientStaminaState.set(
                payload.currentStamina(),
                payload.maxStamina(),
                payload.usageTotal(),
                payload.regenCooldown(),
                payload.regenAmount(),
                payload.exhaustionLevel(),
                payload.lastHurrahUsed(),
                payload.powersDisabled(),
                payload.initialized(),
                payload.upgradePoints(),
                payload.pointsProgress()
        ));
    }
}
