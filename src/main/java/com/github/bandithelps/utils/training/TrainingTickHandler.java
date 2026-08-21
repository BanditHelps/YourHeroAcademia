package com.github.bandithelps.utils.training;

import com.github.bandithelps.YourHeroAcademia;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public class TrainingTickHandler {

    private static final int UPDATE_INTERVAL = 100;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (event.getServer().getTickCount() % UPDATE_INTERVAL == 0) {
            // Every 5 seconds, update the player's training results
            event.getServer().getPlayerList().getPlayers()
                    .forEach(TrainingUtil::updateResults);
        }
    }

}
