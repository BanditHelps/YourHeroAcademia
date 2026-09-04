package com.github.bandithelps.utils.stamina;

import net.threetag.palladium.logic.context.DataContext;
import net.threetag.palladium.logic.value.Value;
import net.threetag.palladium.power.ability.AbilityProperties;

public interface StaminaProperties {

    static StaminaProperties of(AbilityProperties properties) {
        return (StaminaProperties) properties;
    }

    Value yha$getActivationStamina();

    void yha$setActivationStamina(Value value);

    Value yha$getStaminaInterval();

    void yha$setStaminaInterval(Value value);

    Value yha$getStaminaIntervalCost();

    void yha$setStaminaIntervalCost(Value value);

    default int yha$resolveActivationStamina(DataContext context) {
        return resolveInt(this.yha$getActivationStamina(), context);
    }

    default int yha$resolveStaminaInterval(DataContext context) {
        return resolveInt(this.yha$getStaminaInterval(), context);
    }

    default int yha$resolveStaminaIntervalCost(DataContext context) {
        return resolveInt(this.yha$getStaminaIntervalCost(), context);
    }

    private static int resolveInt(Value value, DataContext context) {
        if (value == null) {
            return 0;
        }
        return Math.max(0, value.getAsInt(context));
    }
}
