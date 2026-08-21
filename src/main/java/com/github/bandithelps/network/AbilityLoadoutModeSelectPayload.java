package com.github.bandithelps.network;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.capabilities.loadout.AbilityLoadoutAttachments;
import com.github.bandithelps.capabilities.loadout.AbilityLoadoutData;
import com.github.bandithelps.capabilities.loadout.AbilityLoadoutSyncEvents;
import com.github.bandithelps.utils.loadout.AbilityLoadoutUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityReference;

public record AbilityLoadoutModeSelectPayload(String encodedReference) implements CustomPacketPayload {
    public static final Type<AbilityLoadoutModeSelectPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "ability_loadout_mode_select"));

    public static final StreamCodec<ByteBuf, AbilityLoadoutModeSelectPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            AbilityLoadoutModeSelectPayload::encodedReference,
            AbilityLoadoutModeSelectPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AbilityLoadoutModeSelectPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            AbilityReference reference = AbilityLoadoutData.decodeReference(payload.encodedReference());
            if (reference == null) {
                return;
            }
            AbilityInstance<?> instance = AbilityLoadoutUtil.resolveInstance(player, reference);
            if (instance == null || !instance.isUnlocked() || !AbilityLoadoutUtil.isBarAbility(instance)) {
                return;
            }
            AbilityLoadoutData loadout = AbilityLoadoutAttachments.get(player);
            loadout.setSelectedMode(reference.powerId(), AbilityLoadoutUtil.getListIndex(player, instance), reference.abilityKey());
            AbilityLoadoutSyncEvents.syncNow(player);
        });
    }
}
