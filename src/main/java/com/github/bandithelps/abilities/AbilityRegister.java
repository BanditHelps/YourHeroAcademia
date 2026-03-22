package com.github.bandithelps.abilities;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.bodydata.DamageBodyPartAbility;
import com.github.bandithelps.abilities.movement.DashAbility;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.registry.PalladiumRegistryKeys;

public class AbilityRegister {

    public static final DeferredRegister<AbilitySerializer<?>> ABILITIES;
    public static final DeferredHolder<AbilitySerializer<?>, DashAbility.Serializer> DASH;
    public static final DeferredHolder<AbilitySerializer<?>, DamageBodyPartAbility.Serializer> DAMAGE_BODY_PART;

    static {
        ABILITIES = DeferredRegister.create(PalladiumRegistryKeys.ABILITY_SERIALIZER, YourHeroAcademia.MODID);
        DASH = ABILITIES.register("dash", DashAbility.Serializer::new);
        DAMAGE_BODY_PART = ABILITIES.register("damage_body_part", DamageBodyPartAbility.Serializer::new);
    }

}
