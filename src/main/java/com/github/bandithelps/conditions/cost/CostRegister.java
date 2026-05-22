package com.github.bandithelps.conditions.cost;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.threetag.palladium.logic.cost.CostSerializer;
import net.threetag.palladium.registry.PalladiumRegistryKeys;

public class CostRegister {
    public static final DeferredRegister<CostSerializer<?>> COST_SERIALIZERS;
    public static final DeferredHolder<CostSerializer<?>, UpgradePointCost.Serializer> UPGRADE_POINT;

    static {
        COST_SERIALIZERS = DeferredRegister.create(PalladiumRegistryKeys.COST_SERIALIZER, "yha");
        UPGRADE_POINT = COST_SERIALIZERS.register("upgrade_point", UpgradePointCost.Serializer::new);
    }

}
