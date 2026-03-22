package com.github.bandithelps.values;

import com.github.bandithelps.capabilities.body.BodyPart;
import net.threetag.palladium.documentation.SettingType;
import net.threetag.palladium.entity.PlayerSlot;

public interface ModSettingTypes {
    SettingType TYPE_BODY_PART = SettingType.enumList(BodyPart.exampleValues().stream().map(Object::toString).toList());

}
