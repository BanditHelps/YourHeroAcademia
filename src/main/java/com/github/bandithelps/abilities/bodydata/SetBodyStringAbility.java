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
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;

import java.util.Collections;
import java.util.List;

/**
 * Tiny utility ability that writes a fixed string into the body system the moment it becomes enabled.
 * Handy for letting an {@code palladium:ability_wheel} select a "mode" that other abilities then read
 * (e.g. choosing the active decay pattern).
 */
public class SetBodyStringAbility extends Ability {

    public static final MapCodec<SetBodyStringAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Codec.STRING.optionalFieldOf("part", BodyPart.CHEST.getId()).forGetter((ab) -> ab.part),
                    Codec.STRING.fieldOf("key").forGetter((ab) -> ab.key),
                    Codec.STRING.fieldOf("value").forGetter((ab) -> ab.value),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, SetBodyStringAbility::new));

    public final String part;
    public final String key;
    public final String value;

    public SetBodyStringAbility(String part, String key, String value, AbilityProperties properties, AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
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
            IBodyData body = BodyAttachments.get(player);
            body.setCustomString(player, bodyPart, this.key, this.value);
            BodySyncEvents.syncNow(player);
        }
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.SET_BODY_STRING.get();
    }

    public static class Serializer extends AbilitySerializer<SetBodyStringAbility> {
        public MapCodec<SetBodyStringAbility> codec() {
            return SetBodyStringAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, SetBodyStringAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Writes a fixed string value into a body part's custom data when the ability becomes enabled.")
                    .add("part", ModSettingTypes.TYPE_BODY_PART, "The body part to store the string in.")
                    .add("key", TYPE_STRING, "The key to store the string under.")
                    .add("value", TYPE_STRING, "The string value to store.")
                    .addExampleObject(new SetBodyStringAbility(BodyPart.CHEST.getId(), "decay_pattern", "all", AbilityProperties.BASIC, AbilityStateManager.EMPTY, Collections.emptyList()));
        }
    }
}
