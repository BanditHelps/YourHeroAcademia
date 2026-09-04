package com.github.bandithelps.client.floatquirk;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.floatquirk.FloatAbility;
import com.github.bandithelps.abilities.floatquirk.FloatAnimPose;
import com.github.bandithelps.abilities.floatquirk.FloatPhysics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Registers client input, blocks WASD while floating, applies Float physics
 * once per client tick so local gravity stays in sync, and ticks pose state
 * for every client player so Palladium controllers can read lean/sit.
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
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer local = minecraft.player;
        ClientLevel level = minecraft.level;
        if (local != null && FloatAbility.isActive(local)) {
            FloatPhysics.Session session = FloatPhysics.sessionOf(local);
            if (session != null) {
                FloatPhysics.tick(local, session.resolvedMaxHeight, session.resolvedMaxSpeed);
            }
        }
        if (level == null) {
            return;
        }
        for (Player player : level.players()) {
            if (FloatAbility.isActive(player)) {
                FloatAnimPose.tick(player);
            } else {
                FloatAnimPose.clear(player.getUUID());
            }
        }
    }
}
