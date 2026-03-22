package com.github.bandithelps.utils.stamina;

import net.threetag.palladium.power.ability.AbilityProperties;

public interface StaminaProperties {

    static StaminaProperties of(AbilityProperties properties) {
        return (StaminaProperties) properties;
    }

    int yha$getActivationStamina();

    void yha$setActivationStamina(int value);

    int yha$getStaminaInterval();

    void yha$setStaminaInterval(int value);

    int yha$getStaminaIntervalCost();

    void yha$setStaminaIntervalCost(int value);
}
