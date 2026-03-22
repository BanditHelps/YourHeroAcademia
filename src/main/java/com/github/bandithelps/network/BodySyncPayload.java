package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.body.ClientBodyState;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BodySyncPayload(CompoundTag bodyDataTag) implements CustomPacketPayload {
    public static final Type<BodySyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "body_sync"));

    public static final StreamCodec<ByteBuf, BodySyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG,
            BodySyncPayload::bodyDataTag,
            BodySyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BodySyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientBodyState.set(payload.bodyDataTag()));
    }
}
