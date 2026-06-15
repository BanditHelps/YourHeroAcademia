package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.BlackwhipStruggleTapPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Converts the local player's jump presses into struggle taps while restrained, and swallows the jump
 * so they can't simply hop away.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class BlackwhipStruggleClient {

    private static boolean wasJumpDown = false;

    private BlackwhipStruggleClient() {
    }

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null || !ClientBlackwhipStruggleState.isActive()) {
            wasJumpDown = false;
            return;
        }

        boolean jumpDown = minecraft.options.keyJump.isDown();
        if (jumpDown && !wasJumpDown) {
            ClientPacketDistributor.sendToServer(BlackwhipStruggleTapPayload.INSTANCE);
        }
        wasJumpDown = jumpDown;
        minecraft.options.keyJump.setDown(false);
    }
}
