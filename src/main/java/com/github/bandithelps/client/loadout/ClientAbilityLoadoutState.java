package com.github.bandithelps.client.loadout;

import com.github.bandithelps.capabilities.loadout.AbilityLoadoutData;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.threetag.palladium.power.ability.AbilityReference;

public final class ClientAbilityLoadoutState {
    private static final AbilityLoadoutData DATA = new AbilityLoadoutData();

    private ClientAbilityLoadoutState() {
    }

    public static AbilityLoadoutData get() {
        return DATA;
    }

    public static void apply(List<String> slots, Map<String, String> modes) {
        DATA.setEncodedSlots(slots);
        DATA.setEncodedModes(modes);
    }

    public static AbilityReference getSlot(int index) {
        return DATA.getSlot(index);
    }

    public static String getSelectedMode(Identifier powerId, int listIndex) {
        return DATA.getSelectedMode(powerId, listIndex);
    }
}
