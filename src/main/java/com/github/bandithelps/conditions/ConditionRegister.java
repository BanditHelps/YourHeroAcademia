package com.github.bandithelps.conditions;

import com.github.bandithelps.YourHeroAcademia;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.threetag.palladium.logic.condition.ConditionSerializer;
import net.threetag.palladium.registry.PalladiumRegistryKeys;

public class ConditionRegister {
    public static final DeferredRegister<ConditionSerializer<?>> CONDITIONS;
    public static final DeferredHolder<ConditionSerializer<?>, BodyHealthCondition.Serializer> BODY_HEALTH;

    static {
        CONDITIONS = DeferredRegister.create(PalladiumRegistryKeys.CONDITION_SERIALIZER, YourHeroAcademia.MODID);
        BODY_HEALTH = CONDITIONS.register("body_health", BodyHealthCondition.Serializer::new);
    }

}
