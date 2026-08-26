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

public record CreationResearchPayload(String id, int kind) implements CustomPacketPayload {
    public static final int KIND_ITEM = 0;
    public static final int KIND_ENCHANT = 1;
    public static final int KIND_POTION = 2;

    public static final Type<CreationResearchPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "creation_research"));

    public static final StreamCodec<ByteBuf, CreationResearchPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CreationResearchPayload::id,
            ByteBufCodecs.VAR_INT,
            CreationResearchPayload::kind,
            CreationResearchPayload::new
    );

    public CreationResearchPayload(String id) {
        this(id, KIND_ITEM);
    }

    public CreationResearchPayload(String id, boolean enchant) {
        this(id, enchant ? KIND_ENCHANT : KIND_ITEM);
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
                if (payload.kind() == KIND_ENCHANT) {
                    CreationUtil.trySacrificeEnchant(player, parsed);
                } else if (payload.kind() == KIND_POTION) {
                    CreationUtil.trySacrificePotion(player, parsed);
                } else {
                    CreationUtil.trySacrifice(player, parsed);
                }
            } catch (RuntimeException ignored) {
            }
        });
    }
}
