package com.github.bandithelps.capabilities.loadout;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.threetag.palladium.power.ability.AbilityReference;

public class AbilityLoadoutData {
    public static final int SLOT_COUNT = 5;

    public static final MapCodec<AbilityLoadoutData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.listOf().optionalFieldOf("slots", List.of()).forGetter(AbilityLoadoutData::encodedSlots),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("modes", Map.of()).forGetter(AbilityLoadoutData::encodedModes)
    ).apply(instance, AbilityLoadoutData::fromCodec));

    private final AbilityReference[] slots = new AbilityReference[SLOT_COUNT];
    private final Map<String, String> selectedModes = new LinkedHashMap<>();

    public AbilityLoadoutData() {
    }

    private static AbilityLoadoutData fromCodec(List<String> slots, Map<String, String> modes) {
        AbilityLoadoutData data = new AbilityLoadoutData();
        data.setEncodedSlots(slots);
        data.selectedModes.clear();
        if (modes != null) {
            data.selectedModes.putAll(modes);
        }
        return data;
    }

    public boolean hasAnyAssigned() {
        for (AbilityReference slot : this.slots) {
            if (slot != null) {
                return true;
            }
        }
        return false;
    }

    public AbilityReference getSlot(int index) {
        if (index < 0 || index >= SLOT_COUNT) {
            return null;
        }
        return this.slots[index];
    }

    public void setSlot(int index, AbilityReference reference) {
        if (index < 0 || index >= SLOT_COUNT) {
            return;
        }
        this.slots[index] = reference;
    }

    public void clearSlot(int index) {
        setSlot(index, null);
    }

    public void clearAll() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            this.slots[i] = null;
        }
        this.selectedModes.clear();
    }

    public int indexOf(AbilityReference reference) {
        if (reference == null) {
            return -1;
        }
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (reference.equals(this.slots[i])) {
                return i;
            }
        }
        return -1;
    }

    public String getSelectedMode(Identifier powerId, int listIndex) {
        return this.selectedModes.get(modeKey(powerId, listIndex));
    }

    public void setSelectedMode(Identifier powerId, int listIndex, String abilityKey) {
        if (powerId == null || abilityKey == null || abilityKey.isBlank()) {
            return;
        }
        this.selectedModes.put(modeKey(powerId, listIndex), abilityKey);
    }

    public Map<String, String> getSelectedModes() {
        return Collections.unmodifiableMap(this.selectedModes);
    }

    public void copyFrom(AbilityLoadoutData other) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            this.slots[i] = other.slots[i];
        }
        this.selectedModes.clear();
        this.selectedModes.putAll(other.selectedModes);
    }

    public List<String> encodedSlots() {
        List<String> encoded = new ArrayList<>(SLOT_COUNT);
        for (int i = 0; i < SLOT_COUNT; i++) {
            encoded.add(encodeReference(this.slots[i]));
        }
        return encoded;
    }

    public Map<String, String> encodedModes() {
        return new LinkedHashMap<>(this.selectedModes);
    }

    public void setEncodedSlots(List<String> encoded) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            this.slots[i] = null;
        }
        if (encoded == null) {
            return;
        }
        int count = Math.min(SLOT_COUNT, encoded.size());
        for (int i = 0; i < count; i++) {
            this.slots[i] = decodeReference(encoded.get(i));
        }
    }

    public void setEncodedModes(Map<String, String> modes) {
        this.selectedModes.clear();
        if (modes != null) {
            this.selectedModes.putAll(modes);
        }
    }

    public static String modeKey(Identifier powerId, int listIndex) {
        return powerId + "|" + listIndex;
    }

    public static String encodeReference(AbilityReference reference) {
        if (reference == null) {
            return "";
        }
        return reference.powerId() + "#" + reference.abilityKey();
    }

    public static AbilityReference decodeReference(String encoded) {
        if (encoded == null || encoded.isBlank() || !encoded.contains("#")) {
            return null;
        }
        try {
            return AbilityReference.parse(encoded);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
