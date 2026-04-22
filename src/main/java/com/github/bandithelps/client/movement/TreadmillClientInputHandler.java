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

    private TreadmillClientInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            ClientTreadmillState.setMounted(false);
            ClientTreadmillState.clearMinigame();
            return;
        }

        if (minecraft.screen != null) {
            return;
        }

        if (!ClientTreadmillState.isMounted() || minecraft.options.keyShift.isDown()) {
            return;
        }

        // Do not clear WASD while the QTE is active. Forcing setDown(false) every tick breaks KeyMapping
        // state so consumeClick() can re-fire every frame (queued wrong keys → instant server failure).
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
            return;
        }

        if (!ClientTreadmillState.isMounted() || minecraft.options.keyShift.isDown()) {
            return;
        }

        sendMinigameInputs(minecraft);
        minecraft.player.setSprinting(true);
        minecraft.player.walkAnimation.update(WALK_ANIMATION_SPEED, WALK_ANIMATION_DAMPING, WALK_ANIMATION_SCALE);
    }

    private static void sendMinigameInputs(Minecraft minecraft) {
        if (!ClientTreadmillState.isMinigameActive()) {
            return;
        }
        // At most one key per frame so we never send multiple wrong inputs if multiple edges register.
        if (minecraft.options.keyUp.consumeClick()) {
            ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(0));
        } else if (minecraft.options.keyLeft.consumeClick()) {
            ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(1));
        } else if (minecraft.options.keyDown.consumeClick()) {
            ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(2));
        } else if (minecraft.options.keyRight.consumeClick()) {
            ClientPacketDistributor.sendToServer(new TreadmillMinigameInputPayload(3));
        }
    }
}
