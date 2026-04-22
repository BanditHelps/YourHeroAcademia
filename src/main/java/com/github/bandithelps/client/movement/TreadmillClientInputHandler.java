package com.github.bandithelps.client.movement;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

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
            return;
        }

        if (minecraft.screen != null) {
            return;
        }

        if (!ClientTreadmillState.isMounted() || minecraft.options.keyShift.isDown()) {
            return;
        }

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
            return;
        }

        if (!ClientTreadmillState.isMounted() || minecraft.options.keyShift.isDown()) {
            return;
        }

        minecraft.player.setSprinting(true);
        minecraft.player.walkAnimation.update(WALK_ANIMATION_SPEED, WALK_ANIMATION_DAMPING, WALK_ANIMATION_SCALE);
    }
}
