package com.github.bandithelps.capabilities.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class BodyPartData {
    private static final String BASE_MAX_HEALTH_KEY = "baseMaxHealth";
    private static final String MAX_HEALTH_MODIFIER_KEY = "maxHealthModifier";
    private static final String CURRENT_HEALTH_KEY = "currentHealth";
    private static final String PROSTHETIC_KEY = "prosthetic";
    private static final String CUSTOM_FLOATS_KEY = "customFloats";
    private static final String CUSTOM_STRINGS_KEY = "customStrings";

    public static final Codec<BodyPartData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf(BASE_MAX_HEALTH_KEY, 100.0F).forGetter(BodyPartData::getBaseMaxHealth),
            Codec.FLOAT.optionalFieldOf(MAX_HEALTH_MODIFIER_KEY, 0.0F).forGetter(BodyPartData::getMaxHealthModifier),
            Codec.FLOAT.optionalFieldOf(CURRENT_HEALTH_KEY, 100.0F).forGetter(BodyPartData::getCurrentHealth),
            Codec.BOOL.optionalFieldOf(PROSTHETIC_KEY, false).forGetter(BodyPartData::isProsthetic),
            Codec.unboundedMap(Codec.STRING, Codec.FLOAT).optionalFieldOf(CUSTOM_FLOATS_KEY, Map.of()).forGetter(BodyPartData::getCustomFloats),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf(CUSTOM_STRINGS_KEY, Map.of()).forGetter(BodyPartData::getCustomStrings)
    ).apply(instance, BodyPartData::fromCodec));

    private float baseMaxHealth = 100.0F;
    private float maxHealthModifier;
    private float currentHealth = 100.0F;
    private boolean prosthetic;
    private final Map<String, Float> customFloats = new HashMap<>();
    private final Map<String, String> customStrings = new HashMap<>();

    public BodyPartData() {
    }

    public BodyPartData(BodyPartData other) {
        this.baseMaxHealth = other.baseMaxHealth;
        this.maxHealthModifier = other.maxHealthModifier;
        this.currentHealth = other.currentHealth;
        this.prosthetic = other.prosthetic;
        this.customFloats.putAll(other.customFloats);
        this.customStrings.putAll(other.customStrings);
    }

    private static BodyPartData fromCodec(
            float baseMaxHealth,
            float maxHealthModifier,
            float currentHealth,
            boolean prosthetic,
            Map<String, Float> customFloats,
            Map<String, String> customStrings
    ) {
        BodyPartData data = new BodyPartData();
        data.setBaseMaxHealth(baseMaxHealth);
        data.setMaxHealthModifier(maxHealthModifier);
        data.setCurrentHealth(currentHealth);
        data.setProsthetic(prosthetic);
        data.customFloats.putAll(customFloats);
        data.customStrings.putAll(customStrings);
        return data;
    }

    public BodyPartData copy() {
        return new BodyPartData(this);
    }

    public float getBaseMaxHealth() {
        return baseMaxHealth;
    }

    public void setBaseMaxHealth(float baseMaxHealth) {
        this.baseMaxHealth = Math.max(1.0F, baseMaxHealth);
        if (currentHealth > getMaxHealth()) {
            currentHealth = getMaxHealth();
        }
    }

    public float getMaxHealthModifier() {
        return maxHealthModifier;
    }

    public void setMaxHealthModifier(float maxHealthModifier) {
        this.maxHealthModifier = maxHealthModifier;
        if (currentHealth > getMaxHealth()) {
            currentHealth = getMaxHealth();
        }
    }

    public float getMaxHealth() {
        return Math.max(1.0F, baseMaxHealth + maxHealthModifier);
    }

    public float getCurrentHealth() {
        return currentHealth;
    }

    public void setCurrentHealth(float currentHealth) {
        this.currentHealth = Math.max(0.0F, Math.min(currentHealth, getMaxHealth()));
    }

    public void damage(float amount) {
        if (amount <= 0.0F) {
            return;
        }
        setCurrentHealth(currentHealth - amount);
    }

    public void heal(float amount) {
        if (amount <= 0.0F) {
            return;
        }
        setCurrentHealth(currentHealth + amount);
    }

    public DamageState getDamageState() {
        return DamageState.fromHealth(currentHealth, getMaxHealth());
    }

    public boolean isProsthetic() {
        return prosthetic;
    }

    public void setProsthetic(boolean prosthetic) {
        this.prosthetic = prosthetic;
    }

    public Map<String, Float> getCustomFloats() {
        return Collections.unmodifiableMap(customFloats);
    }

    public float getCustomFloat(String key, float defaultValue) {
        return customFloats.getOrDefault(key, defaultValue);
    }

    public void setCustomFloat(String key, float value) {
        customFloats.put(key, value);
    }

    public void removeCustomFloat(String key) {
        customFloats.remove(key);
    }

    public Map<String, String> getCustomStrings() {
        return Collections.unmodifiableMap(customStrings);
    }

    public String getCustomString(String key) {
        return customStrings.get(key);
    }

    public void setCustomString(String key, String value) {
        customStrings.put(key, value);
    }

    public void removeCustomString(String key) {
        customStrings.remove(key);
    }
}
