package com.github.bandithelps.utils.stamina;

import com.github.bandithelps.capabilities.stamina.IStaminaData;
import com.github.bandithelps.capabilities.stamina.StaminaAttachments;
import com.github.bandithelps.capabilities.stamina.StaminaSyncEvents;
import com.github.bandithelps.values.ModDamageTypes;
import com.github.bandithelps.values.StaminaConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameType;

import java.util.Objects;

import static com.github.bandithelps.values.StaminaConstants.*;

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
            if (regenRate >= 1 || player.getRandom().nextFloat() <= regenRate) {
                int newCurrent = Math.min(current + regenAmount, max);
                stamina.setCurrentStamina(newCurrent);

                if (newCurrent <= 0) {
                    int exhaustLevel = stamina.getExhaustionLevel();
                    int newExhaustionLevel = calculateExhaustionLevel(newCurrent);

                    if (exhaustLevel != newExhaustionLevel) { removeExhaustionEffects(player); }

                    // Notify the player of a decrease in exhaustion
                    if (newExhaustionLevel < exhaustLevel) {
                        // TODO add in some better indication of the exhaustion changing
                         player.sendSystemMessage(Component.literal("Updated exhaustion"));
                    }

                    stamina.setExhaustionLevel(newExhaustionLevel);
                }
            }
        }

        // We re-get this to ensure it is updated to the current stamina value
        int exhaustLevel = stamina.getExhaustionLevel();
        applyExhaustionEffects(player, exhaustLevel);

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
        if (!player.isAlive()) return;

        IStaminaData stamina = StaminaAttachments.get(player);

        int exhaustionLevel = stamina.getExhaustionLevel();
        int newCurrent = stamina.getCurrentStamina() - amount;
        int newExhaustionLevel = calculateExhaustionLevel(newCurrent);

        calcUpgradeProgress(player, newCurrent, stamina.getMaxStamina(), stamina.getPointsProgress(), amount, exhaustionLevel, stamina);

        stamina.setCurrentStamina(newCurrent);
        stamina.setExhaustionLevel(newExhaustionLevel);
        stamina.setRegenCooldown(STAMINA_REGEN_COOLDOWNS[newExhaustionLevel]);
        stamina.setUsageTotal(stamina.getUsageTotal() + amount);

        handleMaxStaminaIncrease(player, exhaustionLevel, stamina.getUsageTotal(), stamina);


        // Check to see if the player is at the death level for exhaustion
        if (exhaustionLevel == EXHAUSTION_DEATH_LEVEL) {
            // Always use normal damage application so vanilla death/respawn flow remains intact.
            ModDamageTypes.applyExhaustionDamage(player, Float.MAX_VALUE);
            return;
        } else if (exhaustionLevel > 1) { // Otherwise, see if they just take normal damage
            ModDamageTypes.applyExhaustionDamage(player, EXHAUSTION_DAMAGE_LEVELS[exhaustionLevel]);
        }

        StaminaSyncEvents.syncNow(player);
    }

    /**
     * Check the new stamina value, and see if it falls into one of the exhaustion levels.
     * Return the index (or level 0-based) of the exhaustion.
     * @param stamina - the stamina value to check the level of (will usually be negative if exhausted)
     * @return
     */
    private static int calculateExhaustionLevel(int stamina) {
        for (int i = 0; i < EXHAUSTION_LEVELS.length - 1; i++) {
            if (stamina >= EXHAUSTION_LEVELS[i]) return i;
        }

        return EXHAUSTION_LEVELS.length - 1;
    }

    /**
     * This function is responsible for applying the negative effects based on the level of exhaustion that the player
     * has. These range from slowness to physical damage, with the idea that they increase in strength, the higher
     * the value goes.
     * @param player
     * @param exhaustLevel
     */
    private static void applyExhaustionEffects(ServerPlayer player, int exhaustLevel) {
        AttributeMap attributes = player.getAttributes();

        // Slowness gets applied at every level, so do it at this level
        AttributeModifier attackSpeedModifier = new AttributeModifier(EXHAUSTION_ATTACK_SLOW_ATTRIBUTE, EXHAUSTION_ATTACK_SLOW_MODIFIERS[exhaustLevel], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        AttributeModifier slownessModifier = new AttributeModifier(EXHAUSTION_MOVE_SLOW_ATTRIBUTE, EXHAUSTION_SLOWNESS_MODIFIERS[exhaustLevel], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        AttributeModifier digSlowModifier = new AttributeModifier(EXHAUSTION_DIG_SLOW_ATTRIBUTE, EXHAUSTION_DIG_SLOW_MODIFIERS[exhaustLevel], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        AttributeModifier jumpModifier = new AttributeModifier(EXHAUSTION_JUMP_ATTRIBUTE, EXHAUSTION_JUMP_MODIFIERS[exhaustLevel], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        AttributeModifier weaknessModifier = new AttributeModifier(EXHAUSTION_WEAKNESS_ATTRIBUTE, EXHAUSTION_WEAKNESS_MODIFIERS[exhaustLevel], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);


        Objects.requireNonNull(attributes.getInstance(Attributes.ATTACK_SPEED)).addOrUpdateTransientModifier(attackSpeedModifier);
        Objects.requireNonNull(attributes.getInstance(Attributes.MOVEMENT_SPEED)).addOrUpdateTransientModifier(slownessModifier);
        Objects.requireNonNull(attributes.getInstance(Attributes.BLOCK_BREAK_SPEED)).addOrUpdateTransientModifier(digSlowModifier);
        Objects.requireNonNull(attributes.getInstance(Attributes.JUMP_STRENGTH)).addOrUpdateTransientModifier(jumpModifier);
        Objects.requireNonNull(attributes.getInstance(Attributes.ATTACK_DAMAGE)).addOrUpdateTransientModifier(weaknessModifier);
    }

    /**
     * Removes all the exhaustion attributes so that they can be reapplied when the exhaustion level changes
     * @param player
     */
    private static void removeExhaustionEffects(ServerPlayer player) {
        AttributeMap attributes = player.getAttributes();
        Objects.requireNonNull(attributes.getInstance(Attributes.ATTACK_SPEED)).removeModifier(EXHAUSTION_ATTACK_SLOW_ATTRIBUTE);
        Objects.requireNonNull(attributes.getInstance(Attributes.MOVEMENT_SPEED)).removeModifier(EXHAUSTION_MOVE_SLOW_ATTRIBUTE);
        Objects.requireNonNull(attributes.getInstance(Attributes.BLOCK_BREAK_SPEED)).removeModifier(EXHAUSTION_DIG_SLOW_ATTRIBUTE);
        Objects.requireNonNull(attributes.getInstance(Attributes.JUMP_STRENGTH)).removeModifier(EXHAUSTION_JUMP_ATTRIBUTE);
        Objects.requireNonNull(attributes.getInstance(Attributes.ATTACK_DAMAGE)).removeModifier(EXHAUSTION_WEAKNESS_ATTRIBUTE);
    }

    /**
     * Based on the amount of stamina that the player used and their exhaustion level, increase their stamina progress
     * accordingly. They only get progress if their stamina is below the progress gain threshold
     * @param player
     * @param staminaUsed
     * @param exhaustLevel
     */
    private static void calcUpgradeProgress(ServerPlayer player, int currentStamina, int maxStamina, int curProgress, int staminaUsed, int exhaustLevel, IStaminaData stamina) {
        // Only award upgrade if they are below a certain percentage of their max stamina
        if (((float) currentStamina / maxStamina) <= STAMINA_GAIN_PERCENTAGE) {
            int progressGain = (int) Math.floor(staminaUsed * EXHAUSTION_MULTIPLIERS[exhaustLevel]);
            int newProgress = progressGain + curProgress;

            int pointsToAward = newProgress / UPGRADE_POINT_COST;
            int finalProgress = newProgress % UPGRADE_POINT_COST;

            stamina.setPointsProgress(finalProgress);

            if (pointsToAward > 0) {
                // TODO add a ding or something to tell them they upgraded
                stamina.setUpgradePoints(stamina.getUpgradePoints() + pointsToAward);
            }

            StaminaSyncEvents.syncNow(player);
        }
    }

    /**
     * When the player has used more than STAMINA_GAIN_REQ stamina, randomly check to see if they are awarded more
     * stamina.
     * @param player
     * @param exhuastLevel
     * @param newUsageTotal
     * @param stamina
     */
    private static void handleMaxStaminaIncrease(ServerPlayer player, int exhuastLevel, int newUsageTotal, IStaminaData stamina) {
        double chanceToIncrease = exhuastLevel > 0 ? STAMINA_GAIN_EXHAUSTED_CHANCE : STAMINA_GAIN_CHANCE;

        if (newUsageTotal >= STAMINA_GAIN_REQ && player.getRandom().nextFloat() < chanceToIncrease) {
            int maxIncrease = (int) Math.floor(player.getRandom().nextFloat() * STAMINA_MAX_INCREASE) + 1;
            stamina.setMaxStamina(stamina.getMaxStamina() + maxIncrease);
            stamina.setUsageTotal(0);
            StaminaSyncEvents.syncNow(player);
        }
    }

    /**
     * This function resets all the stamina values to their baseline values, primarily used when a player dies
     * @param player
     */
    public static void handlePlayerDeath(ServerPlayer player) {
        IStaminaData stamina = StaminaAttachments.get(player);

        stamina.setCurrentStamina(stamina.getMaxStamina());
        stamina.setRegenCooldown(0);
        stamina.setExhaustionLevel(0);
        stamina.setLastHurrahUsed(false);
        stamina.setUsageTotal(0);
        stamina.setPointsProgress(0);
        stamina.setPowersDisabled(false);
        StaminaSyncEvents.syncNow(player);
    }


    /*
    =============================================
    |              Debug Functions              |
    =============================================
     */

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
        stamina.setExhaustionLevel(calculateExhaustionLevel(newCurrent));
        StaminaSyncEvents.syncNow(player);
    }

    public static void forceSetStaminaData(ServerPlayer player, int amount, String dataName) {
        IStaminaData stamina = StaminaAttachments.get(player);

        switch (dataName) {
            case "regenAmount" -> stamina.setRegenAmount(amount);
            case "current" -> stamina.setCurrentStamina(amount);
            case "max" -> stamina.setMaxStamina(amount);
            case "exhaustionLevel" -> stamina.setExhaustionLevel(amount);
        }

        StaminaSyncEvents.syncNow(player);
    }

}