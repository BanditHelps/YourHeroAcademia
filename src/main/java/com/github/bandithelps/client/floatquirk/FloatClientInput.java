package com.github.bandithelps.client.floatquirk;

import com.github.bandithelps.abilities.floatquirk.FloatAbility;
import com.github.bandithelps.abilities.floatquirk.FloatPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Reads jump / sneak for Float vertical control, and eats WASD so vanilla
 * air-strafe cannot add horizontal speed.
 */
public final class FloatClientInput {

    private FloatClientInput() {
    }

    public static void register() {
        FloatPhysics.clientInput = FloatClientInput::read;
    }

    public static void suppressHorizontalKeys() {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer local = minecraft.player;
        if (local == null || minecraft.screen != null || !FloatAbility.isActive(local)) {
            return;
        }
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyRight.setDown(false);
        minecraft.options.keySprint.setDown(false);
    }

    public static FloatPhysics.Control read(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer local = minecraft.player;
        if (local == null || local != entity) {
            return FloatPhysics.Control.NONE;
        }
        return new FloatPhysics.Control(
                minecraft.options.keyJump.isDown(),
                minecraft.options.keyShift.isDown());
    }
}
