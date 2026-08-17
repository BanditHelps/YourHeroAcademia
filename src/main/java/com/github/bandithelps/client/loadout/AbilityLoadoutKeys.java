package com.github.bandithelps.client.loadout;

import net.minecraft.client.KeyMapping;
import net.threetag.palladium.client.PalladiumKeyMappings;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

public final class AbilityLoadoutKeys {
    public static final KeyMapping MODE_SELECT = new KeyMapping(
            "key.yha.ability_mode_select",
            GLFW.GLFW_KEY_LEFT_ALT,
            PalladiumKeyMappings.CATEGORY
    );

    private AbilityLoadoutKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(MODE_SELECT);
    }

    public static boolean isModeSelectDown() {
        return MODE_SELECT.isDown();
    }
}
