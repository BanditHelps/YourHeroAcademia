package com.github.bandithelps.values;

import com.github.bandithelps.YourHeroAcademia;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.threetag.palladium.logic.value.ValueSerializer;
import net.threetag.palladium.registry.PalladiumRegistryKeys;

public class ValueRegister {
    public static final DeferredRegister<ValueSerializer<?>> VALUES;
    public static final DeferredHolder<ValueSerializer<?>, UpgradeSwitchValue.Serializer> UPGRADE_SWITCH;

    static {
        VALUES = DeferredRegister.create(PalladiumRegistryKeys.VALUE_SERIALIZER, YourHeroAcademia.MODID);
        UPGRADE_SWITCH = VALUES.register("upgrade_switch", UpgradeSwitchValue.Serializer::new);
    }
}
