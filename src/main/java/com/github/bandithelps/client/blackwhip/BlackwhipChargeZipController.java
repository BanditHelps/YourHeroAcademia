package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Suppresses locomotion input and applies the Charge Zip reverse slide on the local player.
 * Look is left free; launch still uses the facing from charge start.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class BlackwhipChargeZipController {

    private static final float MAX_STRETCH = 6.0f;

    private BlackwhipChargeZipController() {
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        if (!ClientBlackwhipChargeZipState.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyRight.setDown(false);
        minecraft.options.keyJump.setDown(false);
        minecraft.options.keySprint.setDown(false);
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        if (!ClientBlackwhipChargeZipState.isActive()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        applyPullback(player);
    }

    private static void applyPullback(LocalPlayer player) {
        if (player.horizontalCollision) {
            return;
        }
        Vec3 look = ClientBlackwhipChargeZipState.launchLook();
        Vec3 back = new Vec3(-look.x, 0.0, -look.z);
        if (back.lengthSqr() < 1.0e-6) {
            return;
        }
        back = back.normalize();
        float speed = ClientBlackwhipChargeZipState.getPullbackSpeed();
        if (speed <= 0.0f) {
            return;
        }
        float ratio = ClientBlackwhipChargeZipState.getChargeRatio();
        speed *= 0.7f + 0.3f * ratio;
        double stretch = player.position().distanceTo(ClientBlackwhipChargeZipState.getStartPos());
        if (stretch >= MAX_STRETCH) {
            Vec3 vel = player.getDeltaMovement();
            player.setDeltaMovement(0.0, vel.y, 0.0);
            return;
        }
        Vec3 vel = player.getDeltaMovement();
        player.setDeltaMovement(back.x * speed, vel.y, back.z * speed);
        player.hurtMarked = true;
    }
}
