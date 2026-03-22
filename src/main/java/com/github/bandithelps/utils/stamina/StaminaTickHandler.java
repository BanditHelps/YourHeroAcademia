package com.github.bandithelps.utils.stamina;

import com.github.bandithelps.YourHeroAcademia;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
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

    @SubscribeEvent
    public static void onDamageDone(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof Husk) {
            System.out.println("Damage: " + event.getOriginalDamage());
        }
    }

}
