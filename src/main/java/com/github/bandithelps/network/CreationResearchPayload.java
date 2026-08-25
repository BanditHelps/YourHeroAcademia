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

public record CreationResearchPayload(String id, boolean enchant) implements CustomPacketPayload {
    public static final Type<CreationResearchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "creation_research"));

    public static final StreamCodec<ByteBuf, CreationResearchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CreationResearchPayload::id,
            ByteBufCodecs.BOOL,
            CreationResearchPayload::enchant,
            CreationResearchPayload::new
    );

    public CreationResearchPayload(String id) {
        this(id, false);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreationResearchPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                Identifier parsed = Identifier.parse(payload.id());
                if (payload.enchant()) {
                    CreationUtil.trySacrificeEnchant(player, parsed);
                } else {
                    CreationUtil.trySacrifice(player, parsed);
                }
            } catch (RuntimeException ignored) {
            }
        });
    }
}
