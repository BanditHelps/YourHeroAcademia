package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.network.BlackwhipChainChargeZipPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client snapshot for the local player's Charge Zip hold: launch facing, pullback, and charge HUD.
 */
public final class ClientBlackwhipChargeZipState {

    private static volatile boolean active;
    private static volatile float launchYaw;
    private static volatile float launchPitch;
    private static volatile float chargeRatio;
    private static volatile float pullbackSpeed;
    private static Vec3 startPos = Vec3.ZERO;

    private ClientBlackwhipChargeZipState() {
    }

    public static void apply(BlackwhipChainChargeZipPayload payload) {
        boolean wasActive = active;
        active = payload.active();
        if (!payload.active()) {
            if (wasActive) {
                clearHud();
            }
            chargeRatio = 0.0f;
            pullbackSpeed = 0.0f;
            startPos = Vec3.ZERO;
            return;
        }
        launchYaw = payload.yaw();
        launchPitch = payload.pitch();
        chargeRatio = Mth.clamp(payload.chargeRatio(), 0.0f, 1.0f);
        pullbackSpeed = Math.max(0.0f, payload.pullbackSpeed());
        if (!wasActive) {
            LocalPlayer player = Minecraft.getInstance().player;
            startPos = player != null ? player.position() : Vec3.ZERO;
        }
        updateChargeText();
    }

    public static void clear() {
        boolean wasActive = active;
        active = false;
        chargeRatio = 0.0f;
        pullbackSpeed = 0.0f;
        startPos = Vec3.ZERO;
        if (wasActive) {
            clearHud();
        }
    }

    public static Vec3 getStartPos() {
        return startPos;
    }

    public static boolean isActive() {
        return active;
    }

    public static float getChargeRatio() {
        return chargeRatio;
    }

    public static float getPullbackSpeed() {
        return pullbackSpeed;
    }

    /** Initial look from when the charge started; used for pullback and launch, not camera. */
    public static Vec3 launchLook() {
        float yawRad = launchYaw * Mth.DEG_TO_RAD;
        float pitchRad = launchPitch * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitchRad);
        return new Vec3(-Mth.sin(yawRad) * cosPitch, -Mth.sin(pitchRad), Mth.cos(yawRad) * cosPitch);
    }

    private static void updateChargeText() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui == null) {
            return;
        }
        int percent = Math.round(chargeRatio * 100.0f);
        Component text = Component.translatable("ability.yha.blackwhip_chain_charge_zip.charging", percent);
        minecraft.gui.setOverlayMessage(text, false);
    }

    private static void clearHud() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.gui != null) {
            minecraft.gui.setOverlayMessage(Component.empty(), false);
        }
    }
}
