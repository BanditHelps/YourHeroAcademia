package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.network.BlackwhipChainReelScrollPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Captures mouse scroll while Puppet is held and Lead is on, forwarding notches to the server
 * and cancelling hotbar switching.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class BlackwhipChainReelClient {

    private BlackwhipChainReelClient() {
    }

    @SubscribeEvent
    public static void onMouseScroll(InputEvent.MouseScrollingEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen != null) {
            return;
        }
        if (!ClientBlackwhipChainReelState.isActive() || !ClientBlackwhipChainLeadState.isActive()) {
            return;
        }
        double scrollY = event.getScrollDeltaY();
        if (Math.abs(scrollY) < 1.0e-4) {
            return;
        }
        // Scroll up (+Y) extends; scroll down retracts.
        int direction = scrollY > 0.0 ? -1 : 1;
        ClientPacketDistributor.sendToServer(new BlackwhipChainReelScrollPayload(direction));
        event.setCanceled(true);
    }
}
