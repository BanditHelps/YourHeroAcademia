package com.github.bandithelps.client.movement;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.TreadmillMinigameInputPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class TreadmillClientInputHandler {
    private static final float WALK_ANIMATION_SPEED = 0.9F;
    private static final float WALK_ANIMATION_DAMPING = 0.2F;
    private static final float WALK_ANIMATION_SCALE = 1.0F;

    private static boolean keyPressedAtTickStart = false;
    private static boolean keyLeftPressedAtTickStart = false;
    private static boolean keyDownPressedAtTickStart = false;
    private static boolean keyRightPressedAtTickStart = false;

    private static boolean wasKeyPressedLastTick = false;
    private static boolean wasKeyLeftPressedLastTick = false;
    private static boolean wasKeyDownPressedLastTick = false;
    private static boolean wasKeyRightPressedLastTick = false;

    private TreadmillClientInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            ClientTreadmillState.setMounted(false);
            ClientTreadmillState.clearMinigame();
            resetKeyStates();
            return;
        }

        if (minecraft.screen != null) {
            return;
        }

        if (!ClientTreadmillState.isMounted() || minecraft.options.keyShift.isDown()) {
            return;
        }

        keyPressedAtTickStart = minecraft.options.keyUp.isDown();
        keyLeftPressedAtTickStart = minecraft.options.keyLeft.isDown();
        keyDownPressedAtTickStart = minecraft.options.keyDown.isDown();
        keyRightPressedAtTickStart = minecraft.options.keyRight.isDown();

        minecraft.options.keyUp.setDown(false);
        minecraft.options.keyDown.setDown(false);
        minecraft.options.keyLeft.setDown(false);
        minecraft.options.keyRight.setDown(false);
        minecraft.options.keyJump.setDown(false);
        minecraft.options.keySprint.setDown(false);
        minecraft.player.setDeltaMovement(0.0D, Math.min(0.0D, minecraft.player.getDeltaMovement().y), 0.0D);
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            resetKeyStates();
            return;
        }

        if (!ClientTreadmillState.isMounted() || minecraft.options.keyShift.isDown()) {
            resetKeyStates();
            return;
        }

        if (ClientTreadmillState.isMinigameActive()) {
            if (!wasKeyPressedLastTick && keyPressedAtTickStart) {
                ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(0));
            } else if (!wasKeyLeftPressedLastTick && keyLeftPressedAtTickStart) {
                ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(1));
            } else if (!wasKeyDownPressedLastTick && keyDownPressedAtTickStart) {
                ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(2));
            } else if (!wasKeyRightPressedLastTick && keyRightPressedAtTickStart) {
                ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(3));
            }
        }

        wasKeyPressedLastTick = keyPressedAtTickStart;
        wasKeyLeftPressedLastTick = keyLeftPressedAtTickStart;
        wasKeyDownPressedLastTick = keyDownPressedAtTickStart;
        wasKeyRightPressedLastTick = keyRightPressedAtTickStart;

        minecraft.player.setSprinting(true);
        minecraft.player.walkAnimation.update(WALK_ANIMATION_SPEED, WALK_ANIMATION_DAMPING, WALK_ANIMATION_SCALE);
    }

    private static void resetKeyStates() {
        keyPressedAtTickStart = false;
        keyLeftPressedAtTickStart = false;
        keyDownPressedAtTickStart = false;
        keyRightPressedAtTickStart = false;
        wasKeyPressedLastTick = false;
        wasKeyLeftPressedLastTick = false;
        wasKeyDownPressedLastTick = false;
        wasKeyRightPressedLastTick = false;
    }
}