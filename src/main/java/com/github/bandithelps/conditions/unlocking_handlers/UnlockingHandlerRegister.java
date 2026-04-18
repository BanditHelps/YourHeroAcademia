package com.github.bandithelps.conditions.unlocking_handlers;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.threetag.palladium.power.ability.unlocking.ExperienceLevelBuyableUnlockingHandler;
import net.threetag.palladium.power.ability.unlocking.UnlockingHandlerSerializer;
import net.threetag.palladium.registry.PalladiumRegistryKeys;

public class UnlockingHandlerRegister {
    public static final DeferredRegister<UnlockingHandlerSerializer<?>> UNLOCKING_HANDLERS;
    public static final DeferredHolder<UnlockingHandlerSerializer<?>, UpgradePointBuyHandler.Serializer> UPGRADE_BUYABLE;

    static {
        UNLOCKING_HANDLERS = DeferredRegister.create(PalladiumRegistryKeys.ABILITY_UNLOCKING_HANDLER_SERIALIZER, "yha");
        UPGRADE_BUYABLE = UNLOCKING_HANDLERS.register("upgrade_point_buyable", UpgradePointBuyHandler.Serializer::new);
    }


}
