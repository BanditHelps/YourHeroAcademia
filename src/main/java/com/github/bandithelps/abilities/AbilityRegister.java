package com.github.bandithelps.abilities;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.bodydata.DisplayBodyBarAbility;
import com.github.bandithelps.abilities.bodydata.BodyPartValueTickAbility;
import com.github.bandithelps.abilities.bodydata.DamageBodyPartAbility;
import com.github.bandithelps.abilities.bodydata.HealBodyPartAbility;
import com.github.bandithelps.abilities.cloud.CreateSmokeCloudAbility;
import com.github.bandithelps.abilities.cloud.DisperseCloudDomeAbility;
import com.github.bandithelps.abilities.movement.DashAbility;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.threetag.palladium.power.ability.AbilitySerializer;
import net.threetag.palladium.registry.PalladiumRegistryKeys;

public class AbilityRegister {

    public static final DeferredRegister<AbilitySerializer<?>> ABILITIES;
    public static final DeferredHolder<AbilitySerializer<?>, DashAbility.Serializer> DASH;
    public static final DeferredHolder<AbilitySerializer<?>, DamageBodyPartAbility.Serializer> DAMAGE_BODY_PART;
    public static final DeferredHolder<AbilitySerializer<?>, HealBodyPartAbility.Serializer> HEAL_BODY_PART;
    public static final DeferredHolder<AbilitySerializer<?>, BodyPartValueTickAbility.Serializer> CHANGE_BODY_VALUE;
    public static final DeferredHolder<AbilitySerializer<?>, DisplayBodyBarAbility.Serializer> DISPLAY_BODY_BAR;
    public static final DeferredHolder<AbilitySerializer<?>, CreateSmokeCloudAbility.Serializer> CREATE_SMOKE_CLOUD;
    public static final DeferredHolder<AbilitySerializer<?>, DisperseCloudDomeAbility.Serializer> DISPERSE_CLOUD_DOME;

    static {
        ABILITIES = DeferredRegister.create(PalladiumRegistryKeys.ABILITY_SERIALIZER, YourHeroAcademia.MODID);
        DASH = ABILITIES.register("dash", DashAbility.Serializer::new);
        DAMAGE_BODY_PART = ABILITIES.register("damage_body_part", DamageBodyPartAbility.Serializer::new);
        HEAL_BODY_PART = ABILITIES.register("heal_body_part", HealBodyPartAbility.Serializer::new);
        CHANGE_BODY_VALUE = ABILITIES.register("change_body_value", BodyPartValueTickAbility.Serializer::new);
        DISPLAY_BODY_BAR = ABILITIES.register("display_body_bar", DisplayBodyBarAbility.Serializer::new);
        CREATE_SMOKE_CLOUD = ABILITIES.register("create_smoke_cloud", CreateSmokeCloudAbility.Serializer::new);
        DISPERSE_CLOUD_DOME = ABILITIES.register("disperse_cloud_dome", DisperseCloudDomeAbility.Serializer::new);
    }

}
