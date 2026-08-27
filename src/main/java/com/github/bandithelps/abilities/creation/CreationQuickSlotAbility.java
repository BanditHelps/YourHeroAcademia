package com.github.bandithelps.abilities.creation;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.creation.CreationUtil;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

public class CreationQuickSlotAbility extends Ability {
    public static final MapCodec<CreationQuickSlotAbility> CODEC = RecordCodecBuilder.mapCodec(instance ->
            instance.group(
                    Codec.intRange(0, 5).optionalFieldOf("slot", 0).forGetter(ability -> ability.slot),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()
            ).apply(instance, CreationQuickSlotAbility::new));

    public final int slot;

    public CreationQuickSlotAbility(
            int slot,
            AbilityProperties properties,
            AbilityStateManager conditions,
            List<EnergyBarUsage> energyBarUsages
    ) {
        super(properties, conditions, energyBarUsages);
        this.slot = slot;
    }

    public int slot() {
        return this.slot;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (!(entity instanceof ServerPlayer player)) {
            return;
        }
        CreationUtil.tryActivateQuickSlot(player, this.slot);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.CREATION_QUICK_SLOT.get();
    }

    public static class Serializer extends AbilitySerializer<CreationQuickSlotAbility> {
        @Override
        public MapCodec<CreationQuickSlotAbility> codec() {
            return CODEC;
        }

        @Override
        public void addDocumentation(CodecDocumentationBuilder<Ability, CreationQuickSlotAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Creates the recipe assigned to a Creation quick-craft slot.")
                    .add("slot", TYPE_INT, "Zero-based quick-slot index (0-5).")
                    .addExampleObject(new CreationQuickSlotAbility(0, AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
