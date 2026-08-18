package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.values.ModSettingTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.threetag.palladium.documentation.CodecDocumentationBuilder;
import net.threetag.palladium.logic.value.StaticValue;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityProperties;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.power.ability.AbilityStateManager;
import net.threetag.palladium.power.energybar.EnergyBarUsage;
import net.threetag.palladium.util.PalladiumCodecs;

import java.util.Collections;
import java.util.List;

/**
 * Toggle that stops stamina drain and converts spent stamina into body-part damage.
 */
public class BlackwhipBodyReinforceAbility extends Ability {

    public static final MapCodec<BlackwhipBodyReinforceAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    Value.CODEC.optionalFieldOf("stamina_to_damage", new StaticValue(0.5f)).forGetter((ab) -> ab.staminaToDamage),
                    PalladiumCodecs.listOrPrimitive(Codec.STRING).optionalFieldOf("parts", defaultPhysicalPartIds())
                            .forGetter((ab) -> ab.parts),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipBodyReinforceAbility::new));

    public final Value staminaToDamage;
    public final List<String> parts;

    public BlackwhipBodyReinforceAbility(Value staminaToDamage, List<String> parts, AbilityProperties properties,
                                         AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.staminaToDamage = staminaToDamage;
        this.parts = parts;
    }

    public static List<String> defaultPhysicalPartIds() {
        return BodyPart.physicalParts().stream().map(BodyPart::getId).toList();
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_BODY_REINFORCE.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipBodyReinforceAbility> {
        public MapCodec<BlackwhipBodyReinforceAbility> codec() {
            return BlackwhipBodyReinforceAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipBodyReinforceAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("While toggled on, ability stamina is not drained. The stamina that would have been spent is converted into body-part damage.")
                    .add("stamina_to_damage", TYPE_VALUE, "Body damage applied to each listed part per point of stamina that would have been spent. Default 0.5.")
                    .add("parts", ModSettingTypes.TYPE_BODY_PART, "Body parts that receive the converted damage. Defaults to every physical part.")
                    .addExampleObject(new BlackwhipBodyReinforceAbility(
                            new StaticValue(0.5f),
                            defaultPhysicalPartIds(),
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            Collections.emptyList()));
        }
    }
}
