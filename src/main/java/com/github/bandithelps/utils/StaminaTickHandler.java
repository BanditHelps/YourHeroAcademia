package com.github.bandithelps.utils;

import com.github.bandithelps.YourHeroAcademia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public class StaminaTickHandler {

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % 20 == 0) {
            event.getServer().getPlayerList().getPlayers()
                    .forEach(StaminaUtil::handleStaminaTick);
        }
    }

}
