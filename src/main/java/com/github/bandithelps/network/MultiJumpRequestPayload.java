package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.abilities.movement.MultiJumpAbility;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityUtil;

public record MultiJumpRequestPayload() implements CustomPacketPayload {
    public static final MultiJumpRequestPayload INSTANCE = new MultiJumpRequestPayload();
    public static final Type<MultiJumpRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "multi_jump_request"));
    public static final StreamCodec<ByteBuf, MultiJumpRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MultiJumpRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }

            for (AbilityInstance<MultiJumpAbility> instance : AbilityUtil.getEnabledInstances(player, AbilityRegister.MULTI_JUMP.get())) {
                if (!instance.isUnlocked()) {
                    continue;
                }

                if (instance.getAbility().tryPerformJump(player)) {
                    break;
                }
            }
        });
    }
}
