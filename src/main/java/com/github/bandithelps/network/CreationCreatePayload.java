package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.creation.CreationUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CreationCreatePayload(String itemId) implements CustomPacketPayload {
    public static final Type<CreationCreatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "creation_create"));

    public static final StreamCodec<ByteBuf, CreationCreatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CreationCreatePayload::itemId,
            CreationCreatePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreationCreatePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                CreationUtil.tryCreate(player, Identifier.parse(payload.itemId()));
            } catch (RuntimeException ignored) {
            }
        });
    }
}
