package com.github.bandithelps.capabilities.creation;

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
    public static final int QUICK_SLOT_COUNT = 3;

    public static final MapCodec<CreationData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("progress", Map.of()).forGetter(CreationData::encodedProgress),
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(CreationData::encodedUnlocked),
            Codec.STRING.listOf().optionalFieldOf("quickSlots", List.of()).forGetter(CreationData::encodedQuickSlots)
    ).apply(instance, CreationData::fromCodec));

    private final Map<Identifier, Integer> progress = new LinkedHashMap<>();
    private final Set<Identifier> unlocked = new LinkedHashSet<>();
    private final Identifier[] quickSlots = new Identifier[QUICK_SLOT_COUNT];

    public CreationData() {
    }

    private static CreationData fromCodec(Map<String, Integer> progress, List<String> unlocked, List<String> slots) {
        CreationData data = new CreationData();
        if (progress != null) {
            progress.forEach((key, value) -> {
                Identifier id = parseId(key);
                if (id != null && value != null && value > 0) {
                    data.progress.put(id, value);
                }
            });
        }
        if (unlocked != null) {
            for (String key : unlocked) {
                Identifier id = parseId(key);
                if (id != null) {
                    data.unlocked.add(id);
                }
            }
        }
        data.setEncodedQuickSlots(slots);
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

    public Identifier getQuickSlot(int index) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return null;
        }
        return quickSlots[index];
    }

    public void setQuickSlot(int index, Identifier itemId) {
        if (index < 0 || index >= QUICK_SLOT_COUNT) {
            return;
        }
        if (itemId != null) {
            for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
                if (itemId.equals(quickSlots[i]) && i != index) {
                    quickSlots[i] = null;
                }
            }
        }
        quickSlots[index] = itemId;
    }

    public Map<String, Integer> encodedProgress() {
        Map<String, Integer> encoded = new LinkedHashMap<>();
        progress.forEach((id, value) -> encoded.put(id.toString(), value));
        return encoded;
    }

    public List<String> encodedUnlocked() {
        List<String> encoded = new ArrayList<>();
        for (Identifier id : unlocked) {
            encoded.add(id.toString());
        }
        return encoded;
    }

    public List<String> encodedQuickSlots() {
        List<String> encoded = new ArrayList<>(QUICK_SLOT_COUNT);
        for (int i = 0; i < QUICK_SLOT_COUNT; i++) {
            encoded.add(quickSlots[i] == null ? "" : quickSlots[i].toString());
        }
        return encoded;
    }

    public void setEncodedProgress(Map<String, Integer> encoded) {
        progress.clear();
        if (encoded == null) {
            return;
        }
        encoded.forEach((key, value) -> {
            Identifier id = parseId(key);
            if (id != null && value != null && value > 0) {
                progress.put(id, value);
            }
        });
    }

    public void setEncodedUnlocked(List<String> encoded) {
        unlocked.clear();
        if (encoded == null) {
            return;
        }
        for (String key : encoded) {
            Identifier id = parseId(key);
            if (id != null) {
                unlocked.add(id);
            }
        }
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
            quickSlots[i] = parseId(encoded.get(i));
        }
    }

    public Set<Identifier> unlockedView() {
        return Collections.unmodifiableSet(unlocked);
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
