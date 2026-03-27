package com.github.bandithelps.abilities.bodydata;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.capabilities.body.IBodyData;
import com.github.bandithelps.values.ModSettingTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.*;
import net.threetag.palladium.power.energybar.EnergyBarUsage;
import net.threetag.palladium.util.PalladiumCodecs;

import java.util.Collections;
import java.util.List;

public class HealBodyPartAbility extends Ability {

    public static final MapCodec<HealBodyPartAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("heal_amount", new StaticValue(2.0f)).forGetter((ab) -> ab.healAmount),
                    PalladiumCodecs.listOrPrimitive(Codec.STRING).fieldOf("parts").forGetter((ab) -> ab.parts),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, HealBodyPartAbility::new));

    public final Value healAmount;
    public final List<String> parts;

    public HealBodyPartAbility(Value healAmount, List<String> parts, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.healAmount = healAmount;
        this.parts = parts;
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player) {
            IBodyData body = BodyAttachments.get(player);

            float heal = this.healAmount.getAsFloat(DataContext.forEntity(entity));

            if (heal > 0) {
                boolean requireSync = false;

                for (String partId : this.parts) {
                    BodyPart part = BodyPart.fromId(partId);
                    if (part != null) {
                        body.healPart(player, part, heal);
                        requireSync = true;
                    }
                }

                if (requireSync) {
                    BodySyncEvents.syncNow(player);
                }
            }
        }
        return super.tick(entity, abilityInstance, enabled);
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.HEAL_BODY_PART.get();
    }

    /*
     * Serializer for the documentation
     */
    public static class Serializer extends AbilitySerializer<HealBodyPartAbility> {
        public MapCodec<HealBodyPartAbility> codec() { return HealBodyPartAbility.CODEC; }

        public void addDocumentation(CodecDocumentationBuilder<Ability, HealBodyPartAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Heals the damage of specific body part(s)")
                    .add("heal_amount", TYPE_VALUE, "The amount of damage to be heal to each of the parts.")
                    .add("parts", ModSettingTypes.TYPE_BODY_PART, "A list of the body parts to damage, allowing you to damage multiple parts at once.")
                    .addExampleObject(new HealBodyPartAbility(new StaticValue(5.0f), List.of(BodyPart.CHEST.getId()), AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }

    }


}
