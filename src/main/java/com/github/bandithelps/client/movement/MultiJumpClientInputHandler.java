package com.github.bandithelps.client.movement;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.abilities.AbilityRegister;
import com.github.bandithelps.network.MultiJumpRequestPayload;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.threetag.palladium.power.ability.AbilityInstance;
import net.threetag.palladium.power.ability.AbilityUtil;

@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class MultiJumpClientInputHandler {
    private static boolean wasJumpDown = false;

    private MultiJumpClientInputHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            wasJumpDown = false;
            return;
        }

        boolean jumpDown = minecraft.options.keyJump.isDown();
        if (jumpDown && !wasJumpDown && minecraft.screen == null && !minecraft.player.onGround()) {
            boolean hasUnlockedEnabledMultiJump = AbilityUtil.getEnabledInstances(minecraft.player, AbilityRegister.MULTI_JUMP.get())
                    .stream()
                    .anyMatch(AbilityInstance::isUnlocked);
            if (hasUnlockedEnabledMultiJump) {
                ClientPacketDistributor.sendToServer(MultiJumpRequestPayload.INSTANCE);
            }
        }

        wasJumpDown = jumpDown;
    }
}
