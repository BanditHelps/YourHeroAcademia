package com.github.bandithelps.utils;

import com.github.bandithelps.capabilities.stamina.IStaminaData;
import com.github.bandithelps.capabilities.stamina.StaminaAttachments;
import com.github.bandithelps.capabilities.stamina.StaminaSyncEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Utility for accessing the player's stamina capability and doing all the functions it requires.
 */
public class StaminaUtil {

    public void test(ServerPlayer player) {
        IStaminaData stamina = StaminaAttachments.get(player);
        int current = stamina.getCurrentStamina();
        stamina.setCurrentStamina(current - 10);
        StaminaSyncEvents.syncNow(player);
    }

    /**
     * This function gets called by the @StaminaTickHandler, which is ran every 20 server ticks.
     * Here we handle stamina regeneration cooldowns, power disabling due to low stamina, and other similar checks
     * @param player
     */
    public static void handleStaminaTick(ServerPlayer player) {
        IStaminaData stamina = StaminaAttachments.get(player);

        int current = stamina.getCurrentStamina();
        int max = stamina.getMaxStamina();

        if (current < max) {
            stamina.setCurrentStamina(current + 1);
        }

        // Always need to sync to the player
        StaminaSyncEvents.syncNow(player);

    }

    /**
     * The main method that will be called whenever stamina is used via an ability.
     * Handles stamina "growth" and current stamina levels.
     * @param player
     * @param amount - The amount of Stamina trying to be used
     */
    public static void useStamina(ServerPlayer player, int amount) {
        if (amount == 0) return;
        // Creative players should not use stamina
        if (player.gameMode.getGameModeForPlayer() == GameType.CREATIVE) return;

        IStaminaData stamina = StaminaAttachments.get(player);

        int newCurrent = stamina.getCurrentStamina() - amount;

        if (newCurrent <= 0) {
            // disable the powers
        }

        stamina.setCurrentStamina(newCurrent);
        StaminaSyncEvents.syncNow(player);
    }

    /**
     * Alternative stamina use functions primarily used for testing. Bypasses creative player protections.
     * Has no checks for positive/negative values.
     * SHOULD NOT BE USED UNLESS FOR DEBUGGING
     * @param player
     * @param amount
     */
    public static void forceUseStamina(ServerPlayer player, int amount) {
        IStaminaData stamina = StaminaAttachments.get(player);
        int newCurrent = stamina.getCurrentStamina() - amount;
        stamina.setCurrentStamina(newCurrent);
        StaminaSyncEvents.syncNow(player);
    }

}