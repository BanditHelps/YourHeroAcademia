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

    private static boolean prevKeyUp    = false;
    private static boolean prevKeyLeft  = false;
    private static boolean prevKeyDown  = false;
    private static boolean prevKeyRight = false;

    private TreadmillClientInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            ClientTreadmillState.setMounted(false);
            ClientTreadmillState.clearMinigame();
            resetPrevKeys();
            return;
        }

        if (minecraft.screen != null) {
            return;
        }

        if (!ClientTreadmillState.isMounted() || minecraft.options.keyShift.isDown()) {
            return;
        }

        if (!ClientTreadmillState.isMinigameActive()) {
            minecraft.options.keyUp.setDown(false);
            minecraft.options.keyDown.setDown(false);
            minecraft.options.keyLeft.setDown(false);
            minecraft.options.keyRight.setDown(false);
        }
        minecraft.options.keyJump.setDown(false);
        minecraft.options.keySprint.setDown(false);
        minecraft.player.setDeltaMovement(0.0D, Math.min(0.0D, minecraft.player.getDeltaMovement().y), 0.0D);
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            resetPrevKeys();
            return;
        }

        if (!ClientTreadmillState.isMounted() || minecraft.options.keyShift.isDown()) {
            resetPrevKeys();
            return;
        }

        boolean currUp    = minecraft.options.keyUp.isDown();
        boolean currLeft  = minecraft.options.keyLeft.isDown();
        boolean currDown  = minecraft.options.keyDown.isDown();
        boolean currRight = minecraft.options.keyRight.isDown();

        if (ClientTreadmillState.isMinigameActive()) {
            sendMinigameInputs(currUp, currLeft, currDown, currRight);
        }

        prevKeyUp    = currUp;
        prevKeyLeft  = currLeft;
        prevKeyDown  = currDown;
        prevKeyRight = currRight;

        minecraft.player.setSprinting(true);
        minecraft.player.walkAnimation.update(WALK_ANIMATION_SPEED, WALK_ANIMATION_DAMPING, WALK_ANIMATION_SCALE);
    }

    private static void sendMinigameInputs(boolean currUp, boolean currLeft, boolean currDown, boolean currRight) {
        if (!prevKeyUp && currUp) {
            ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(0));
        } else if (!prevKeyLeft && currLeft) {
            ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(1));
        } else if (!prevKeyDown && currDown) {
            ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(2));
        } else if (!prevKeyRight && currRight) {
            ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(3));
        }
    }

    private static void resetPrevKeys() {
        prevKeyUp    = false;
        prevKeyLeft  = false;
        prevKeyDown  = false;
        prevKeyRight = false;
    }
}
