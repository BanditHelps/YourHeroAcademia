package com.github.bandithelps.mixin;

import com.github.bandithelps.client.loadout.AbilityLoadoutKeys;
import com.github.bandithelps.client.loadout.AbilityModeSelectClient;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.InputEvent;
import net.threetag.palladium.client.PalladiumKeyMappings;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PalladiumKeyMappings.class)
public abstract class PalladiumKeyMappingsMixin {
    @Inject(method = "clientInput", at = @At("HEAD"), cancellable = true)
    private static void yha$interceptAbilityKeysForModeSelect(InputEvent.Key event, CallbackInfo ci) {
        if (!AbilityLoadoutKeys.isModeSelectDown()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        PalladiumKeyMappings.AbilityKeyMapping[] keys = PalladiumKeyMappings.ABILITY_KEYS;
        if (keys == null) {
            return;
        }
        for (PalladiumKeyMappings.AbilityKeyMapping key : keys) {
            if (key == null || !key.matches(event.getKeyEvent())) {
                continue;
            }
            if (event.getAction() == GLFW.GLFW_PRESS) {
                AbilityModeSelectClient.cycleSlot(key.index - 1);
            }
            ci.cancel();
            return;
        }
    }
}
