package com.github.bandithelps.values;

import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.DamageState;
import net.threetag.palladium.documentation.SettingType;
import net.threetag.palladium.entity.PlayerSlot;

import java.util.Arrays;
import java.util.List;

public interface ModSettingTypes {
    SettingType TYPE_BODY_PART = SettingType.enumList(BodyPart.exampleValues().stream().map(Object::toString).toList());
    SettingType TYPE_DAMAGE_STATE = SettingType.enumList(DamageState.exampleValues().stream().map(Object::toString).toList());
    SettingType TYPE_BODY_HEALTH_CHECK = SettingType.enumList(Arrays.asList("state", "value"));
    SettingType TYPE_BODY_BAR = SettingType.enumList(Arrays.asList("bar", "slider"));

}
