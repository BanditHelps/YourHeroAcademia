package com.github.bandithelps.client.floatquirk;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.floatquirk.FloatAbility;
import com.github.bandithelps.abilities.floatquirk.FloatPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Registers client input, blocks WASD while floating, and applies Float physics
 * once per client tick so local gravity stays in sync.
 */
@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class FloatClientEvents {

    private FloatClientEvents() {
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(FloatClientInput::register);
    }

    @SubscribeEvent
    public static void onClientTickPre(ClientTickEvent.Pre event) {
        FloatClientInput.suppressHorizontalKeys();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !FloatAbility.isActive(player)) {
            return;
        }
        FloatPhysics.Session session = FloatPhysics.sessionOf(player);
        if (session == null) {
            return;
        }
        FloatPhysics.tick(player, session.resolvedMaxHeight, session.resolvedMaxSpeed);
    }
}
