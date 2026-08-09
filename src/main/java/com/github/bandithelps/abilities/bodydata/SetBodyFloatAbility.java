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

import java.util.Collections;
import java.util.List;

public class SetBodyFloatAbility extends Ability {

    public static final MapCodec<SetBodyFloatAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Codec.STRING.optionalFieldOf("part", BodyPart.CHEST.getId()).forGetter((ab) -> ab.part),
                    Codec.STRING.fieldOf("key").forGetter((ab) -> ab.key),
                    Value.CODEC.fieldOf("value").forGetter((ab) -> ab.value),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, SetBodyFloatAbility::new));

    public final String part;
    public final String key;
    public final Value value;

    public SetBodyFloatAbility(String part, String key, Value value, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.part = part;
        this.key = key;
        this.value = value;
    }

    @Override
    public void firstTick(LivingEntity entity, AbilityInstance<?> abilityInstance) {
        if (entity instanceof ServerPlayer player) {
            BodyPart bodyPart = BodyPart.fromId(this.part);
            if (bodyPart == null) {
                return;
            }
            DataContext context = DataContext.forEntity(entity);
            IBodyData body = BodyAttachments.get(player);
            body.setCustomFloat(player, bodyPart, this.key, this.value.getAsFloat(context));
            BodySyncEvents.syncNow(player);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.SET_BODY_FLOAT.get();
    }

    public static class Serializer extends AbilitySerializer<SetBodyFloatAbility> {
        public MapCodec<SetBodyFloatAbility> codec() {
            return SetBodyFloatAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, SetBodyFloatAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Writes a fixed float value into a body part's custom data when the ability becomes enabled.")
                    .add("part", ModSettingTypes.TYPE_BODY_PART, "The body part to store the string in.")
                    .add("key", TYPE_STRING, "The key to store the string under.")
                    .add("value", TYPE_FLOAT_VALUE, "The float value to store.")
                    .addExampleObject(new SetBodyFloatAbility(BodyPart.CHEST.getId(), "max_tethers", new StaticValue(1.0f), AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
    
}
