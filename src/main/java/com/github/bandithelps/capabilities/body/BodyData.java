package com.github.bandithelps.capabilities.body;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class BodyData implements IBodyData {
    private static final String PARTS_KEY = "parts";
    private static final String DISPLAY_BARS_KEY = "displayBars";

    public static final MapCodec<BodyData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(BodyPart.CODEC, BodyPartData.CODEC)
                    .optionalFieldOf(PARTS_KEY, defaultPartMap())
                    .forGetter(BodyData::copyPartMap),
            Codec.unboundedMap(Codec.STRING, displayBarCodec())
                    .optionalFieldOf(DISPLAY_BARS_KEY, Map.of())
                    .forGetter(BodyData::copyDisplayBarMap)
    ).apply(instance, BodyData::fromCodec));

    private final EnumMap<BodyPart, BodyPartData> parts = new EnumMap<>(BodyPart.class);
    private final LinkedHashMap<String, BodyDisplayBar> displayBars = new LinkedHashMap<>();

    public BodyData() {
        initializeDefaults();
    }

    private static BodyData fromCodec(Map<BodyPart, BodyPartData> decodedParts, Map<String, BodyDisplayBar> decodedDisplayBars) {
        BodyData data = new BodyData();
        for (Map.Entry<BodyPart, BodyPartData> entry : decodedParts.entrySet()) {
            BodyPart part = entry.getKey();
            if (!part.isPhysical()) {
                continue;
            }
            data.parts.put(part, entry.getValue().copy());
        }
        for (Map.Entry<String, BodyDisplayBar> entry : decodedDisplayBars.entrySet()) {
            BodyDisplayBar displayBar = entry.getValue();
            data.displayBars.put(displayBar.id(), displayBar);
        }
        return data;
    }

    private static Codec<BodyDisplayBar> displayBarCodec() {
        return RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("id").forGetter(BodyDisplayBar::id),
                Codec.STRING.fieldOf("label").forGetter(BodyDisplayBar::label),
                BodyPart.CODEC.fieldOf("part").forGetter(BodyDisplayBar::part),
                Codec.STRING.fieldOf("key").forGetter(BodyDisplayBar::key),
                Codec.FLOAT.fieldOf("min").forGetter(BodyDisplayBar::minValue),
                Codec.FLOAT.fieldOf("max").forGetter(BodyDisplayBar::maxValue),
                Codec.INT.fieldOf("colorRgb").forGetter(BodyDisplayBar::colorRgb),
                Codec.INT.optionalFieldOf("sliderColorRgb", BodyDisplayBar.DEFAULT_SLIDER_COLOR_RGB).forGetter(BodyDisplayBar::sliderColorRgb),
                Codec.INT.optionalFieldOf("barColorRgb", BodyDisplayBar.DEFAULT_SLIDER_TRACK_COLOR_RGB).forGetter(BodyDisplayBar::barColorRgb),
                Codec.INT.optionalFieldOf("gradientLeftColorRgb", 0x2ECC71).forGetter(BodyDisplayBar::gradientLeftColorRgb),
                Codec.INT.optionalFieldOf("gradientRightColorRgb", 0x2ECC71).forGetter(BodyDisplayBar::gradientRightColorRgb),
                Codec.STRING.optionalFieldOf("type", BodyDisplayBarType.FILL.getId())
                        .xmap(
                                value -> {
                                    BodyDisplayBarType resolved = BodyDisplayBarType.fromId(value);
                                    return resolved == null ? BodyDisplayBarType.FILL : resolved;
                                },
                                BodyDisplayBarType::getId
                        )
                        .forGetter(BodyDisplayBar::type)
        ).apply(instance, BodyDisplayBar::new));
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

    private Map<String, BodyDisplayBar> copyDisplayBarMap() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(displayBars));
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
    public void setDisplayBar(BodyDisplayBar displayBar) {
        displayBars.put(displayBar.id(), displayBar);
    }

    @Override
    public void removeDisplayBar(String id) {
        if (id == null) {
            return;
        }
        displayBars.remove(id.trim());
    }

    @Override
    public Map<String, BodyDisplayBar> getDisplayBarsView() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(displayBars));
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

        CompoundTag displayBarsTag = new CompoundTag();
        for (BodyDisplayBar displayBar : displayBars.values()) {
            CompoundTag barTag = new CompoundTag();
            barTag.putString("id", displayBar.id());
            barTag.putString("label", displayBar.label());
            barTag.putString("part", displayBar.part().getId());
            barTag.putString("key", displayBar.key());
            barTag.putFloat("min", displayBar.minValue());
            barTag.putFloat("max", displayBar.maxValue());
            barTag.putInt("colorRgb", displayBar.colorRgb());
            barTag.putInt("sliderColorRgb", displayBar.sliderColorRgb());
            barTag.putInt("barColorRgb", displayBar.barColorRgb());
            barTag.putInt("gradientLeftColorRgb", displayBar.gradientLeftColorRgb());
            barTag.putInt("gradientRightColorRgb", displayBar.gradientRightColorRgb());
            barTag.putString("type", displayBar.type().getId());
            displayBarsTag.put(displayBar.id(), barTag);
        }
        nbt.put(DISPLAY_BARS_KEY, displayBarsTag);
    }

    @Override
    public void loadNBTData(CompoundTag nbt) {
        initializeDefaults();
        displayBars.clear();
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

        CompoundTag displayBarsTag = nbt.getCompound(DISPLAY_BARS_KEY).orElse(new CompoundTag());
        for (String id : displayBarsTag.keySet()) {
            CompoundTag barTag = displayBarsTag.getCompound(id).orElse(new CompoundTag());
            String savedPart = barTag.getString("part").orElse(BodyPart.CHEST.getId());
            BodyPart part = BodyPart.fromId(savedPart);
            if (part == null) {
                continue;
            }
            String typeId = barTag.getString("type").orElse(BodyDisplayBarType.FILL.getId());
            BodyDisplayBarType type = BodyDisplayBarType.fromId(typeId);
            BodyDisplayBar displayBar = new BodyDisplayBar(
                    barTag.getString("id").orElse(id),
                    barTag.getString("label").orElse(id),
                    part,
                    barTag.getString("key").orElse("value"),
                    barTag.getFloat("min").orElse(0.0F),
                    barTag.getFloat("max").orElse(100.0F),
                    barTag.getInt("colorRgb").orElse(0x2ECC71),
                    barTag.getInt("sliderColorRgb").orElse(BodyDisplayBar.DEFAULT_SLIDER_COLOR_RGB),
                    barTag.getInt("barColorRgb").orElse(barTag.getInt("colorRgb").orElse(0x2ECC71)),
                    barTag.getInt("gradientLeftColorRgb").orElse(barTag.getInt("colorRgb").orElse(0x2ECC71)),
                    barTag.getInt("gradientRightColorRgb").orElse(barTag.getInt("colorRgb").orElse(0x2ECC71)),
                    type == null ? BodyDisplayBarType.FILL : type
            );
            displayBars.put(displayBar.id(), displayBar);
        }
    }
}
