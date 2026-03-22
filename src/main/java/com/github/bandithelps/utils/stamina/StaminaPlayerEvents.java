package com.github.bandithelps.utils.stamina;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public class StaminaPlayerEvents {

    /**
     * Reset stamina values after respawn so the newly recreated player entity
     * always starts from a safe baseline.
     */
    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StaminaUtil.handlePlayerDeath(player);
        }
    }

}
