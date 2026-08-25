package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.creation.CreationUtil;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CreationCreatePayload(String itemId, List<EnchantChoice> enchants) implements CustomPacketPayload {
    public static final Type<CreationCreatePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "creation_create"));

    public static final StreamCodec<ByteBuf, CreationCreatePayload> STREAM_CODEC = StreamCodec.of(
            CreationCreatePayload::encode,
            CreationCreatePayload::decode
    );

    public CreationCreatePayload(String itemId) {
        this(itemId, List.of());
    }

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
                CreationUtil.tryCreate(player, Identifier.parse(payload.itemId()), payload.enchants());
            } catch (RuntimeException ignored) {
            }
        });
    }

    private static void encode(ByteBuf buf, CreationCreatePayload payload) {
        ByteBufCodecs.STRING_UTF8.encode(buf, payload.itemId());
        ByteBufCodecs.VAR_INT.encode(buf, payload.enchants().size());
        for (EnchantChoice choice : payload.enchants()) {
            ByteBufCodecs.STRING_UTF8.encode(buf, choice.enchantId());
            ByteBufCodecs.VAR_INT.encode(buf, choice.level());
        }
    }

    private static CreationCreatePayload decode(ByteBuf buf) {
        String itemId = ByteBufCodecs.STRING_UTF8.decode(buf);
        int count = ByteBufCodecs.VAR_INT.decode(buf);
        List<EnchantChoice> enchants = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            enchants.add(new EnchantChoice(
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.VAR_INT.decode(buf)
            ));
        }
        return new CreationCreatePayload(itemId, enchants);
    }

    public record EnchantChoice(String enchantId, int level) {
    }
}
