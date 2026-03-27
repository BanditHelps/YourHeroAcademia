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

public record CloudVolumeSpawnPayload(CompoundTag cloudTag) implements CustomPacketPayload {
    public static final Type<CloudVolumeSpawnPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "cloud_volume_spawn"));
    public static final StreamCodec<ByteBuf, CloudVolumeSpawnPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.COMPOUND_TAG,
            CloudVolumeSpawnPayload::cloudTag,
            CloudVolumeSpawnPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CloudVolumeSpawnPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            CloudNbt.VolumeHeader header = CloudNbt.readHeader(payload.cloudTag());
            ClientCloudState.upsertSpawn(
                    header.id(),
                    header.origin(),
                    header.cellSize(),
                    header.mode(),
                    header.ttl(),
                    CloudNbt.readDeltas(payload.cloudTag())
            );
        });
    }
}
