package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.creation.CreationPotionForm;
import com.github.bandithelps.creation.CreationUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record CreationCreatePotionPayload(
        String effectId,
        String form,
        int durationTicks,
        int amplifier
) implements CustomPacketPayload {
    public static final Type<CreationCreatePotionPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "creation_create_potion"));

    public static final StreamCodec<ByteBuf, CreationCreatePotionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            CreationCreatePotionPayload::effectId,
            ByteBufCodecs.STRING_UTF8,
            CreationCreatePotionPayload::form,
            ByteBufCodecs.VAR_INT,
            CreationCreatePotionPayload::durationTicks,
            ByteBufCodecs.VAR_INT,
            CreationCreatePotionPayload::amplifier,
            CreationCreatePotionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CreationCreatePotionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            try {
                CreationUtil.tryCreatePotion(
                        player,
                        Identifier.parse(payload.effectId()),
                        CreationPotionForm.fromId(payload.form()),
                        payload.durationTicks(),
                        payload.amplifier()
                );
            } catch (RuntimeException ignored) {
            }
        });
    }
}
