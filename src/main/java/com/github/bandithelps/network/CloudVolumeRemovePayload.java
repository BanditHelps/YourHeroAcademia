package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.cloud.ClientCloudState;
import com.github.bandithelps.cloud.CloudNbt;
import io.netty.buffer.ByteBuf;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CloudVolumeRemovePayload(CompoundTag cloudTag) implements CustomPacketPayload {
    public static final Type<CloudVolumeRemovePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "cloud_volume_remove"));
    public static final StreamCodec<ByteBuf, CloudVolumeRemovePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG,
            CloudVolumeRemovePayload::cloudTag,
            CloudVolumeRemovePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CloudVolumeRemovePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientCloudState.remove(CloudNbt.readVolumeId(payload.cloudTag())));
    }
}
