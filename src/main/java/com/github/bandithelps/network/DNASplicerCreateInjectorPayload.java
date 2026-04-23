package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.blocks.DNASplicerBlockEntity;
import com.github.bandithelps.utils.player.PlayerUtils;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record DNASplicerCreateInjectorPayload(
        BlockPos blockPos,
        boolean useLeftSide
) implements CustomPacketPayload {
    public static final Type<DNASplicerCreateInjectorPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "dna_splicer_create_injector"));

    public static final StreamCodec<ByteBuf, DNASplicerCreateInjectorPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            DNASplicerCreateInjectorPayload::blockPos,
            ByteBufCodecs.BOOL,
            DNASplicerCreateInjectorPayload::useLeftSide,
            DNASplicerCreateInjectorPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(DNASplicerCreateInjectorPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            var level = player.level();
            var be = level.getBlockEntity(payload.blockPos());
            if (be instanceof DNASplicerBlockEntity splicer) {
                var injector = splicer.createInjector(payload.useLeftSide(), player);
                if (!injector.isEmpty()) {
                    PlayerUtils.giveItem(player, injector);
                }
            }
        });
    }
}