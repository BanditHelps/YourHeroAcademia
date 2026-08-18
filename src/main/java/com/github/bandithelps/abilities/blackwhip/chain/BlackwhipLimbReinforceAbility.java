package com.github.bandithelps.abilities.blackwhip.chain;

import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.values.ModSettingTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.entity.player.Player;
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
 * Passive limb wrap. Enabled entirely by JSON conditions (typically body health below 50%).
 * Event handlers read {@link #parts} and {@link #reduction} from the enabled instance.
 */
public class BlackwhipLimbReinforceAbility extends Ability {

    public static final MapCodec<BlackwhipLimbReinforceAbility> CODEC = RecordCodecBuilder.mapCodec((instance) ->
            instance.group(
                    PalladiumCodecs.listOrPrimitive(Codec.STRING).fieldOf("parts").forGetter((ab) -> ab.parts),
                    Value.CODEC.optionalFieldOf("reduction", new StaticValue(0.5f)).forGetter((ab) -> ab.reduction),
                    propertiesCodec(),
                    stateCodec(),
                    energyBarUsagesCodec()).apply(instance, BlackwhipLimbReinforceAbility::new));

    public final List<String> parts;
    public final Value reduction;

    public BlackwhipLimbReinforceAbility(List<String> parts, Value reduction, AbilityProperties properties,
                                         AbilityStateManager conditions, List<EnergyBarUsage> energyBarUsages) {
        super(properties, conditions, energyBarUsages);
        this.parts = parts;
        this.reduction = reduction;
    }

    public boolean protects(Player player, BodyPart part) {
        if (player == null || part == null || this.parts == null || this.parts.isEmpty()) {
            return false;
        }
        BodyPart resolved = BodyPart.resolveForPlayer(player, part);
        for (String partId : this.parts) {
            BodyPart listed = BodyPart.fromId(partId);
            if (listed != null && BodyPart.resolveForPlayer(player, listed) == resolved) {
                return true;
            }
        }
        return false;
    }

    @Override
    public AbilitySerializer<?> getSerializer() {
        return AbilityRegister.BLACKWHIP_LIMB_REINFORCE.get();
    }

    public static class Serializer extends AbilitySerializer<BlackwhipLimbReinforceAbility> {
        public MapCodec<BlackwhipLimbReinforceAbility> codec() {
            return BlackwhipLimbReinforceAbility.CODEC;
        }

        public void addDocumentation(CodecDocumentationBuilder<Ability, BlackwhipLimbReinforceAbility> builder, HolderLookup.Provider provider) {
            builder.setDescription("Passive Blackwhip wrap that reduces incoming body damage to the listed limbs while enabled.")
                    .add("parts", ModSettingTypes.TYPE_BODY_PART, "Body parts protected while this ability is enabled.")
                    .add("reduction", TYPE_VALUE, "Fraction of body damage prevented for protected parts. 0.5 = 50% reduction.")
                    .addExampleObject(new BlackwhipLimbReinforceAbility(
                            List.of(BodyPart.LEFT_ARM.getId(), BodyPart.RIGHT_ARM.getId()),
                            new StaticValue(0.5f),
                            AbilityProperties.BASIC,
                            AbilityStateManager.EMPTY,
                            Collections.emptyList()));
        }
    }
}
