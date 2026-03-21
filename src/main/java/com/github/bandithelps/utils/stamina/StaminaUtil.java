package com.github.bandithelps.utils.stamina;

import com.github.bandithelps.capabilities.stamina.IStaminaData;
import com.github.bandithelps.capabilities.stamina.StaminaAttachments;
import com.github.bandithelps.capabilities.stamina.StaminaSyncEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import static com.github.bandithelps.values.StaminaConstants.STAMINA_REGEN_RATE;

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
        int cooldown = stamina.getRegenCooldown();
        int regenAmount = stamina.getRegenAmount();

        // If the regeneration cooldown is active, obv don't try to give them more stamina
        if (cooldown > 0) {
            stamina.setRegenCooldown(cooldown - 1);
        } else if (current < max) {
            // The regeneration rate is dependent on the current exhaustion level of the player
            double regenRate = STAMINA_REGEN_RATE[stamina.getExhaustionLevel()];

            // If not exhausted, just linearly increase the stamina
            if (regenRate >= 1) {
                int newCurrent = Math.min(current + regenAmount, max);
                stamina.setCurrentStamina(newCurrent);
            }

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

    public static void forceSetStaminaData(ServerPlayer player, int amount, String dataName) {
        IStaminaData stamina = StaminaAttachments.get(player);

        switch (dataName) {
            case "regenAmount" -> stamina.setRegenAmount(amount);
            case "current" -> stamina.setCurrentStamina(amount);
            case "max" -> stamina.setMaxStamina(amount);
        }

        StaminaSyncEvents.syncNow(player);
    }

}