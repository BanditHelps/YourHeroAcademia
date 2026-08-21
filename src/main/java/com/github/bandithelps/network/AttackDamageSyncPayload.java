package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.attributes.ClientAttributeState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record AttackDamageSyncPayload(double attackDamage) implements CustomPacketPayload {
    public static final Type<AttackDamageSyncPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "attack_damage_sync"));

    public static final StreamCodec<ByteBuf, AttackDamageSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE,
            AttackDamageSyncPayload::attackDamage,
            AttackDamageSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AttackDamageSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientAttributeState.setAttackDamage(payload.attackDamage()));
    }
}
