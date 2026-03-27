package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.particles.managed.ClientManagedParticleSettings;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ManagedParticleSizePayload(String target, float sizeScale) implements CustomPacketPayload {
    public static final Type<ManagedParticleSizePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "managed_particle_size"));

    public static final StreamCodec<ByteBuf, ManagedParticleSizePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ManagedParticleSizePayload::target,
            ByteBufCodecs.FLOAT,
            ManagedParticleSizePayload::sizeScale,
            ManagedParticleSizePayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ManagedParticleSizePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            switch (payload.target()) {
                case "cloud" -> ClientManagedParticleSettings.setCloudSmokeSize(payload.sizeScale());
                case "beam" -> ClientManagedParticleSettings.setBeamSmokeSize(payload.sizeScale());
                default -> ClientManagedParticleSettings.setAll(payload.sizeScale());
            }
        });
    }
}
