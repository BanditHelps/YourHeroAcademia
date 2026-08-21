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

public record AbilityLoadoutAssignPayload(int slot, String encodedReference) implements CustomPacketPayload {
    public static final Type<AbilityLoadoutAssignPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "ability_loadout_assign"));

    public static final StreamCodec<ByteBuf, AbilityLoadoutAssignPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT,
            AbilityLoadoutAssignPayload::slot,
            ByteBufCodecs.STRING_UTF8,
            AbilityLoadoutAssignPayload::encodedReference,
            AbilityLoadoutAssignPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AbilityLoadoutAssignPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            int slot = payload.slot();
            if (slot < 0 || slot >= AbilityLoadoutData.SLOT_COUNT) {
                return;
            }
            AbilityLoadoutData loadout = AbilityLoadoutAttachments.get(player);
            AbilityReference reference = AbilityLoadoutData.decodeReference(payload.encodedReference());
            if (reference == null) {
                loadout.clearSlot(slot);
                AbilityLoadoutSyncEvents.syncNow(player);
                return;
            }
            AbilityInstance<?> instance = AbilityLoadoutUtil.resolveInstance(player, reference);
            if (instance == null || !instance.isUnlocked() || !AbilityLoadoutUtil.isBarAbility(instance)) {
                return;
            }
            int existing = loadout.indexOf(reference);
            if (existing >= 0 && existing != slot) {
                loadout.clearSlot(existing);
            }
            loadout.setSlot(slot, reference);
            loadout.setSelectedMode(reference.powerId(), AbilityLoadoutUtil.getListIndex(player, instance), reference.abilityKey());
            AbilityLoadoutSyncEvents.syncNow(player);
        });
    }
}
