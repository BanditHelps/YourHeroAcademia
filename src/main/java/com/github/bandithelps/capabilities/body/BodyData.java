package com.github.bandithelps.capabilities.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

public class BodyData implements IBodyData {
    private static final String PARTS_KEY = "parts";

    public static final MapCodec<BodyData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(BodyPart.CODEC, BodyPartData.CODEC)
                    .optionalFieldOf(PARTS_KEY, defaultPartMap())
                    .forGetter(BodyData::copyPartMap)
    ).apply(instance, BodyData::fromCodec));

    private final EnumMap<BodyPart, BodyPartData> parts = new EnumMap<>(BodyPart.class);

    public BodyData() {
        initializeDefaults();
    }

    private static BodyData fromCodec(Map<BodyPart, BodyPartData> decodedParts) {
        BodyData data = new BodyData();
        for (Map.Entry<BodyPart, BodyPartData> entry : decodedParts.entrySet()) {
            BodyPart part = entry.getKey();
            if (!part.isPhysical()) {
                continue;
            }
            data.parts.put(part, entry.getValue().copy());
        }
        return data;
    }

    private static Map<BodyPart, BodyPartData> defaultPartMap() {
        EnumMap<BodyPart, BodyPartData> defaults = new EnumMap<>(BodyPart.class);
        for (BodyPart part : BodyPart.physicalParts()) {
            defaults.put(part, new BodyPartData());
        }
        return defaults;
    }

    private void initializeDefaults() {
        parts.clear();
        for (BodyPart part : BodyPart.physicalParts()) {
            parts.put(part, new BodyPartData());
        }
    }

    private Map<BodyPart, BodyPartData> copyPartMap() {
        EnumMap<BodyPart, BodyPartData> copy = new EnumMap<>(BodyPart.class);
        for (BodyPart part : BodyPart.physicalParts()) {
            copy.put(part, getPartData(part).copy());
        }
        return copy;
    }

    @Override
    public BodyPart resolvePart(Player player, BodyPart part) {
        return BodyPart.resolveForPlayer(player, part);
    }

    @Override
    public BodyPartData getPartData(Player player, BodyPart part) {
        BodyPart resolved = resolvePart(player, part);
        return getPartData(resolved);
    }

    @Override
    public BodyPartData getPartData(BodyPart physicalPart) {
        if (!physicalPart.isPhysical()) {
            throw new IllegalArgumentException("Body part must be physical: " + physicalPart.getId());
        }
        return parts.computeIfAbsent(physicalPart, ignored -> new BodyPartData());
    }

    @Override
    public Map<BodyPart, BodyPartData> getPhysicalPartsView() {
        return Collections.unmodifiableMap(copyPartMap());
    }

    @Override
    public float getHealth(Player player, BodyPart part) {
        return getPartData(player, part).getCurrentHealth();
    }

    @Override
    public void setHealth(Player player, BodyPart part, float health) {
        getPartData(player, part).setCurrentHealth(health);
    }

    @Override
    public void damagePart(Player player, BodyPart part, float amount) {
        getPartData(player, part).damage(amount);
    }

    @Override
    public void healPart(Player player, BodyPart part, float amount) {
        getPartData(player, part).heal(amount);
    }

    @Override
    public float getMaxHealth(Player player, BodyPart part) {
        return getPartData(player, part).getMaxHealth();
    }

    @Override
    public float getBaseMaxHealth(Player player, BodyPart part) {
        return getPartData(player, part).getBaseMaxHealth();
    }

    @Override
    public void setBaseMaxHealth(Player player, BodyPart part, float baseMaxHealth) {
        getPartData(player, part).setBaseMaxHealth(baseMaxHealth);
    }

    @Override
    public float getMaxHealthModifier(Player player, BodyPart part) {
        return getPartData(player, part).getMaxHealthModifier();
    }

    @Override
    public void setMaxHealthModifier(Player player, BodyPart part, float modifier) {
        getPartData(player, part).setMaxHealthModifier(modifier);
    }

    @Override
    public DamageState getDamageState(Player player, BodyPart part) {
        return getPartData(player, part).getDamageState();
    }

    @Override
    public boolean isProsthetic(Player player, BodyPart part) {
        return getPartData(player, part).isProsthetic();
    }

    @Override
    public void setProsthetic(Player player, BodyPart part, boolean prosthetic) {
        getPartData(player, part).setProsthetic(prosthetic);
    }

    @Override
    public float getCustomFloat(Player player, BodyPart part, String key, float defaultValue) {
        return getPartData(player, part).getCustomFloat(key, defaultValue);
    }

    @Override
    public void setCustomFloat(Player player, BodyPart part, String key, float value) {
        getPartData(player, part).setCustomFloat(key, value);
    }

    @Override
    public void removeCustomFloat(Player player, BodyPart part, String key) {
        getPartData(player, part).removeCustomFloat(key);
    }

    @Override
    public Map<String, Float> getCustomFloats(Player player, BodyPart part) {
        return getPartData(player, part).getCustomFloats();
    }

    @Override
    public String getCustomString(Player player, BodyPart part, String key) {
        return getPartData(player, part).getCustomString(key);
    }

    @Override
    public void setCustomString(Player player, BodyPart part, String key, String value) {
        getPartData(player, part).setCustomString(key, value);
    }

    @Override
    public void removeCustomString(Player player, BodyPart part, String key) {
        getPartData(player, part).removeCustomString(key);
    }

    @Override
    public Map<String, String> getCustomStrings(Player player, BodyPart part) {
        return getPartData(player, part).getCustomStrings();
    }

    @Override
    public void saveNBTData(CompoundTag nbt) {
        CompoundTag partRoot = new CompoundTag();
        for (BodyPart part : BodyPart.physicalParts()) {
            BodyPartData data = getPartData(part);
            CompoundTag partTag = new CompoundTag();
            partTag.putFloat("baseMaxHealth", data.getBaseMaxHealth());
            partTag.putFloat("maxHealthModifier", data.getMaxHealthModifier());
            partTag.putFloat("currentHealth", data.getCurrentHealth());
            partTag.putBoolean("prosthetic", data.isProsthetic());

            CompoundTag floatTag = new CompoundTag();
            for (Map.Entry<String, Float> entry : data.getCustomFloats().entrySet()) {
                floatTag.putFloat(entry.getKey(), entry.getValue());
            }
            partTag.put("customFloats", floatTag);

            CompoundTag stringTag = new CompoundTag();
            for (Map.Entry<String, String> entry : data.getCustomStrings().entrySet()) {
                stringTag.putString(entry.getKey(), entry.getValue());
            }
            partTag.put("customStrings", stringTag);

            partRoot.put(part.getId(), partTag);
        }
        nbt.put(PARTS_KEY, partRoot);
    }

    @Override
    public void loadNBTData(CompoundTag nbt) {
        initializeDefaults();
        CompoundTag partRoot = nbt.getCompound(PARTS_KEY).orElse(new CompoundTag());
        for (BodyPart part : BodyPart.physicalParts()) {
            CompoundTag partTag = partRoot.getCompound(part.getId()).orElse(new CompoundTag());
            BodyPartData data = getPartData(part);
            data.setBaseMaxHealth(partTag.getFloat("baseMaxHealth").orElse(data.getBaseMaxHealth()));
            data.setMaxHealthModifier(partTag.getFloat("maxHealthModifier").orElse(data.getMaxHealthModifier()));
            data.setCurrentHealth(partTag.getFloat("currentHealth").orElse(data.getCurrentHealth()));
            data.setProsthetic(partTag.getBoolean("prosthetic").orElse(data.isProsthetic()));

            CompoundTag floatTag = partTag.getCompound("customFloats").orElse(new CompoundTag());
            for (String key : floatTag.keySet()) {
                data.setCustomFloat(key, floatTag.getFloat(key).orElse(0.0F));
            }

            CompoundTag stringTag = partTag.getCompound("customStrings").orElse(new CompoundTag());
            for (String key : stringTag.keySet()) {
                data.setCustomString(key, stringTag.getString(key).orElse(""));
            }
        }
    }
}
