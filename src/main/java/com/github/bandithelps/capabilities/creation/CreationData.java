package com.github.bandithelps.capabilities.creation;

import com.github.bandithelps.creation.CreationQuickSlot;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.Identifier;

public class CreationData {
    public static final int QUICK_SLOT_COUNT = 6;

    public static final MapCodec<CreationData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("progress", Map.of()).forGetter(CreationData::encodedProgress),
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(CreationData::encodedUnlocked),
            Codec.STRING.listOf().optionalFieldOf("quickSlots", List.of()).forGetter(CreationData::encodedQuickSlots),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("enchantProgress", Map.of()).forGetter(CreationData::encodedEnchantProgress),
            Codec.STRING.listOf().optionalFieldOf("enchantUnlocked", List.of()).forGetter(CreationData::encodedEnchantUnlocked),
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("potionProgress", Map.of()).forGetter(CreationData::encodedPotionProgress),
            Codec.STRING.listOf().optionalFieldOf("potionUnlocked", List.of()).forGetter(CreationData::encodedPotionUnlocked)
    ).apply(instance, CreationData::fromCodec));

    private final Map<Identifier, Integer> progress = new LinkedHashMap<>();
    private final Set<Identifier> unlocked = new LinkedHashSet<>();
    private final CreationQuickSlot[] quickSlots = new CreationQuickSlot[QUICK_SLOT_COUNT];
    private final Map<Identifier, Integer> enchantProgress = new LinkedHashMap<>();
    private final Set<Identifier> enchantUnlocked = new LinkedHashSet<>();
    private final Map<Identifier, Integer> potionProgress = new LinkedHashMap<>();
    private final Set<Identifier> potionUnlocked = new LinkedHashSet<>();

    public CreationData() {
    }

    private static CreationData fromCodec(
            Map<String, Integer> progress,
            List<String> unlocked,
            List<String> slots,
            Map<String, Integer> enchantProgress,
            List<String> enchantUnlocked,
            Map<String, Integer> potionProgress,
            List<String> potionUnlocked
    ) {
        CreationData data = new CreationData();
        data.setEncodedProgress(progress);
        data.setEncodedUnlocked(unlocked);
        data.setEncodedQuickSlots(slots);
        data.setEncodedEnchantProgress(enchantProgress);
        data.setEncodedEnchantUnlocked(enchantUnlocked);
        data.setEncodedPotionProgress(potionProgress);
        data.setEncodedPotionUnlocked(potionUnlocked);
        return data;
    }

    public int getProgress(Identifier itemId) {
        if (itemId == null) {
            return 0;
        }
        return progress.getOrDefault(itemId, 0);
    }

    public void setProgress(Identifier itemId, int value) {
        if (itemId == null) {
            return;
        }
        if (value <= 0) {
            progress.remove(itemId);
        } else {
            progress.put(itemId, value);
        }
    }

    public boolean isUnlocked(Identifier itemId) {
        return itemId != null && unlocked.contains(itemId);
    }

    public void unlock(Identifier itemId) {
        if (itemId == null) {
            return;
        }
        unlocked.add(itemId);
        progress.remove(itemId);
    }

    public boolean lock(Identifier itemId) {
        if (itemId == null) {
            return false;
        }
        boolean changed = unlocked.remove(itemId);
        if (progress.remove(itemId) != null) {
            changed = true;
        }
        return changed;
    }

    public int getEnchantProgress(Identifier enchantId) {
        if (enchantId == null) {
            return 0;
        }
        return enchantProgress.getOrDefault(enchantId, 0);
    }

    public void setEnchantProgress(Identifier enchantId, int value) {
        if (enchantId == null) {
            return;
        }
        if (value <= 0) {
            enchantProgress.remove(enchantId);
        } else {
            enchantProgress.put(enchantId, value);
        }
    }

    public boolean isEnchantUnlocked(Identifier enchantId) {
        return enchantId != null && enchantUnlocked.contains(enchantId);
    }

    public void unlockEnchant(Identifier enchantId) {
        if (enchantId == null) {
            return;
        }
        enchantUnlocked.add(enchantId);
        enchantProgress.remove(enchantId);
    }

    public boolean lockEnchant(Identifier enchantId) {
        if (enchantId == null) {
            return false;
        }
        boolean changed = enchantUnlocked.remove(enchantId);
        if (enchantProgress.remove(enchantId) != null) {
            changed = true;
        }
        return changed;
    }

    public int getPotionProgress(Identifier effectId) {
        if (effectId == null) {
            return 0;
        }
        return potionProgress.getOrDefault(effectId, 0);
    }

    public void setPotionProgress(Identifier effectId, int value) {
        if (effectId == null) {
            return;
        }
        if (value <= 0) {
            potionProgress.remove(effectId);
        } else {
            potionProgress.put(effectId, value);
        }
    }

    public boolean isPotionUnlocked(Identifier effectId) {
        return effectId != null && potionUnlocked.contains(effectId);
    }

    public void unlockPotion(Identifier effectId) {
        if (effectId == null) {
            return;
        }
        potionUnlocked.add(effectId);
        potionProgress.remove(effectId);
    }

    public boolean lockPotion(Identifier effectId) {
        if (effectId == null) {
            return false;
        }
        boolean changed = potionUnlocked.remove(effectId);
        if (potionProgress.remove(effectId) != null) {
            changed = true;
        }
        return changed;
    }

    public CreationQuickSlot getQuickSlot(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return null;
        }
        return quickSlots[index];
    }

    public void setQuickSlot(int index, CreationQuickSlot recipe) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        if (recipe != null) {
            for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
                if (recipe.equals(quickSlots[i]) && i != index) {
                    quickSlots[i] = null;
                }
            }
        }
        quickSlots[index] = recipe;
    }

    public Map<String, Integer> encodedProgress() {
        return encodeMap(progress);
    }

    public List<String> encodedUnlocked() {
        return encodeSet(unlocked);
    }

    public List<String> encodedQuickSlots() {
        List<String> encoded = new ArrayList<>(QUICK_SLOT_COUNT);
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            encoded.add(quickSlots[i] == null ? "" : quickSlots[i].encode());
        }
        return encoded;
    }

    public Map<String, Integer> encodedEnchantProgress() {
        return encodeMap(enchantProgress);
    }

    public List<String> encodedEnchantUnlocked() {
        return encodeSet(enchantUnlocked);
    }

    public Map<String, Integer> encodedPotionProgress() {
        return encodeMap(potionProgress);
    }

    public List<String> encodedPotionUnlocked() {
        return encodeSet(potionUnlocked);
    }

    public void setEncodedProgress(Map<String, Integer> encoded) {
        decodeMap(encoded, progress);
    }

    public void setEncodedUnlocked(List<String> encoded) {
        decodeSet(encoded, unlocked);
    }

    public void setEncodedQuickSlots(List<String> encoded) {
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            quickSlots[i] = null;
        }
        if (encoded == null) {
            return;
        }
        int count = Math.min(QUICK_SLOT_COUNT, encoded.size());
        for (int i = 0; i < count; i++) {
            quickSlots[i] = CreationQuickSlot.parse(encoded.get(i));
        }
    }

    public void setEncodedEnchantProgress(Map<String, Integer> encoded) {
        decodeMap(encoded, enchantProgress);
    }

    public void setEncodedEnchantUnlocked(List<String> encoded) {
        decodeSet(encoded, enchantUnlocked);
    }

    public void setEncodedPotionProgress(Map<String, Integer> encoded) {
        decodeMap(encoded, potionProgress);
    }

    public void setEncodedPotionUnlocked(List<String> encoded) {
        decodeSet(encoded, potionUnlocked);
    }

    public Set<Identifier> unlockedView() {
        return Collections.unmodifiableSet(unlocked);
    }

    private static Map<String, Integer> encodeMap(Map<Identifier, Integer> source) {
        Map<String, Integer> encoded = new LinkedHashMap<>();
        source.forEach((id, value) -> encoded.put(id.toString(), value));
        return encoded;
    }

    private static List<String> encodeSet(Set<Identifier> source) {
        List<String> encoded = new ArrayList<>();
        for (Identifier id : source) {
            encoded.add(id.toString());
        }
        return encoded;
    }

    private static void decodeMap(Map<String, Integer> encoded, Map<Identifier, Integer> target) {
        target.clear();
        if (encoded == null) {
            return;
        }
        encoded.forEach((key, value) -> {
            Identifier id = parseId(key);
            if (id != null && value != null && value > 0) {
                target.put(id, value);
            }
        });
    }

    private static void decodeSet(List<String> encoded, Set<Identifier> target) {
        target.clear();
        if (encoded == null) {
            return;
        }
        for (String key : encoded) {
            Identifier id = parseId(key);
            if (id != null) {
                target.add(id);
            }
        }
    }

    private static Identifier parseId(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Identifier.parse(raw);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
