package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.movement.ClientTreadmillState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record TreadmillMountStatePayload(boolean mounted) implements CustomPacketPayload {
    public static final Type<TreadmillMountStatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "treadmill_mount_state"));

    public static final StreamCodec<ByteBuf, TreadmillMountStatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL,
            TreadmillMountStatePayload::mounted,
            TreadmillMountStatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(TreadmillMountStatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ClientTreadmillState.setMounted(payload.mounted());
            if (!payload.mounted()) {
                ClientTreadmillState.clearMinigame();
            }
        });
    }
}
