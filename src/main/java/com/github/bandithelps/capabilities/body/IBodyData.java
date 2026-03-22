package com.github.bandithelps.capabilities.body;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Map;

public interface IBodyData {
    BodyPart resolvePart(Player player, BodyPart part);

    BodyPartData getPartData(Player player, BodyPart part);
    BodyPartData getPartData(BodyPart physicalPart);

    Map<BodyPart, BodyPartData> getPhysicalPartsView();

    float getHealth(Player player, BodyPart part);
    void setHealth(Player player, BodyPart part, float health);
    void damagePart(Player player, BodyPart part, float amount);
    void healPart(Player player, BodyPart part, float amount);

    float getMaxHealth(Player player, BodyPart part);
    float getBaseMaxHealth(Player player, BodyPart part);
    void setBaseMaxHealth(Player player, BodyPart part, float baseMaxHealth);
    float getMaxHealthModifier(Player player, BodyPart part);
    void setMaxHealthModifier(Player player, BodyPart part, float modifier);

    DamageState getDamageState(Player player, BodyPart part);

    boolean isProsthetic(Player player, BodyPart part);
    void setProsthetic(Player player, BodyPart part, boolean prosthetic);

    float getCustomFloat(Player player, BodyPart part, String key, float defaultValue);
    void setCustomFloat(Player player, BodyPart part, String key, float value);
    void removeCustomFloat(Player player, BodyPart part, String key);
    Map<String, Float> getCustomFloats(Player player, BodyPart part);

    String getCustomString(Player player, BodyPart part, String key);
    void setCustomString(Player player, BodyPart part, String key, String value);
    void removeCustomString(Player player, BodyPart part, String key);
    Map<String, String> getCustomStrings(Player player, BodyPart part);

    void saveNBTData(CompoundTag nbt);
    void loadNBTData(CompoundTag nbt);
}
