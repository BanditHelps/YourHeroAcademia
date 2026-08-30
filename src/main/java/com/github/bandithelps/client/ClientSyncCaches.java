package com.github.bandithelps.client;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.body.ClientBodyState;
import com.github.bandithelps.client.creation.ClientCreationState;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class ClientSyncCaches {
    private static boolean cleared = true;

    private ClientSyncCaches() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        if (Minecraft.getInstance().player == null) {
            if (!cleared) {
                ClientBodyState.clear();
                ClientCreationState.clear();
                cleared = true;
            }
            return;
        }
        cleared = false;
    }
}
