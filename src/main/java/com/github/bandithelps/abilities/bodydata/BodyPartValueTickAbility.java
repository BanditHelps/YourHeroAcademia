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
import net.minecraft.util.ExtraCodecs;
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

/**
 * An ability meant to run continuously in order to update a value inside the body system
 */
public class BodyPartValueTickAbility extends Ability {

    public static final MapCodec<BodyPartValueTickAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.fieldOf("amount").forGetter((ab) -> ab.amount),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("tick_rate", 20).forGetter((ab) -> ab.tickRate),
                    Value.CODEC.optionalFieldOf("max_value", new StaticValue(100.0f)).forGetter((ab) -> ab.maxValue),
                    Value.CODEC.optionalFieldOf("min_value", new StaticValue(0.0f)).forGetter((ab) -> ab.minValue),
                    ExtraCodecs.NON_EMPTY_STRING.fieldOf("key").forGetter((ab) -> ab.key),
                    PalladiumCodecs.listOrPrimitive(Codec.STRING).fieldOf("parts").forGetter((ab) -> ab.parts),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BodyPartValueTickAbility::new));

    public final Value amount;
    public final int tickRate;
    public final Value maxValue;
    public final Value minValue;
    public final String key;
    public final List<String> parts;

    public BodyPartValueTickAbility(Value amount, int tickRate, Value maxValue, Value minvalue, String key, List<String> parts, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.amount = amount;
        this.tickRate = tickRate;
        this.maxValue = maxValue;
        this.minValue = minvalue;
        this.key = key;
        this.parts = parts;
    }

    @Override
    public boolean tick(LivingEntity entity, AbilityInstance<?> abilityInstance, boolean enabled) {
        if (enabled && entity instanceof ServerPlayer player) {

            if (entity.tickCount % this.tickRate == 0) {
                IBodyData body = BodyAttachments.get(player);
                boolean requireSync = false;

                float amount = this.amount.getAsFloat(DataContext.forEntity(entity));
                float minValue = this.minValue.getAsFloat(DataContext.forEntity(entity));
                float maxValue = this.maxValue.getAsFloat(DataContext.forEntity(entity));

                for (String partId : this.parts) {
                    BodyPart part = BodyPart.fromId(partId);
                    if (part != null) {
                        float cur = body.getCustomFloat(player, part, key, 0);
                        float newValue = Math.max(minValue ,Math.min(cur + amount, maxValue));

                        if (newValue != cur) {
                            body.setCustomFloat(player, part, key, newValue);
                            requireSync = true;
                        }
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
        return AbilityRegister.CHANGE_BODY_VALUE.get();
    }

    /*
     * Serializer for the documentation
     */
    public static class Serializer extends AbilitySerializer<BodyPartValueTickAbility> {
        public MapCodec<BodyPartValueTickAbility> codec() { return BodyPartValueTickAbility.CODEC; }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BodyPartValueTickAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Changes a float stored inside of one or more body parts by a specific value, every interval number of ticks.")
                    .add("amount", TYPE_VALUE, "The amount of to change the custom value by")
                    .add("tick_rate", TYPE_INT, "How many ticks before incrementing/decrementing the custom value")
                    .add("min_value", TYPE_VALUE, "The lowest that the new value can be.")
                    .add("max_value", TYPE_VALUE, "The highest that the new value can be.")
                    .add("max_value", TYPE_STRING, "The key of the value to store/update")
                    .add("parts", ModSettingTypes.TYPE_BODY_PART, "The body part values to increase")
                    .addExampleObject(new BodyPartValueTickAbility(new StaticValue(5.0f), 20, new StaticValue(0.0f), new StaticValue(100.0f), "example_key", List.of(BodyPart.CHEST.getId()), AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
