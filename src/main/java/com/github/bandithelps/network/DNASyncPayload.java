package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.dna.ClientDNAState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DNASyncPayload(
        String dna,
        boolean hasDNA,
        int intelligence,
        boolean dnaFatigued
) implements CustomPacketPayload {
    public static final Type<DNASyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "dna_sync"));

    public static final StreamCodec<ByteBuf, DNASyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            DNASyncPayload::dna,
            ByteBufCodecs.BOOL,
            DNASyncPayload::hasDNA,
            ByteBufCodecs.VAR_INT,
            DNASyncPayload::intelligence,
            ByteBufCodecs.BOOL,
            DNASyncPayload::dnaFatigued,
            DNASyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DNASyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientDNAState.set(
                payload.dna(),
                payload.hasDNA(),
                payload.intelligence(),
                payload.dnaFatigued()
        ));
    }
}