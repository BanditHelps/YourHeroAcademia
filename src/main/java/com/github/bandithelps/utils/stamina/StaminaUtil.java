package com.github.bandithelps.utils.stamina;

import com.github.bandithelps.capabilities.stamina.IStaminaData;
import com.github.bandithelps.capabilities.stamina.StaminaAttachments;
import com.github.bandithelps.capabilities.stamina.StaminaSyncEvents;
import com.github.bandithelps.values.ModDamageTypes;
import com.github.bandithelps.values.StaminaConstants;
import net.minecraft.resources.Identifier;
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

    private static final AttributeModifier[] EXHAUSTION_ATTACK_SPEED_MODIFIERS =
            createExhaustionModifiers(EXHAUSTION_ATTACK_SLOW_ATTRIBUTE, EXHAUSTION_ATTACK_SLOW_MODIFIERS);
    private static final AttributeModifier[] EXHAUSTION_MOVE_SPEED_MODIFIERS =
            createExhaustionModifiers(EXHAUSTION_MOVE_SLOW_ATTRIBUTE, EXHAUSTION_SLOWNESS_MODIFIERS);
    private static final AttributeModifier[] EXHAUSTION_DIG_SPEED_MODIFIERS =
            createExhaustionModifiers(EXHAUSTION_DIG_SLOW_ATTRIBUTE, EXHAUSTION_DIG_SLOW_MODIFIERS);
    private static final AttributeModifier[] EXHAUSTION_JUMP_STRENGTH_MODIFIERS =
            createExhaustionModifiers(EXHAUSTION_JUMP_ATTRIBUTE, EXHAUSTION_JUMP_MODIFIERS);
    private static final AttributeModifier[] EXHAUSTION_ATTACK_DAMAGE_MODIFIERS =
            createExhaustionModifiers(EXHAUSTION_WEAKNESS_ATTRIBUTE, EXHAUSTION_WEAKNESS_MODIFIERS);

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
        boolean shouldSync = false;

        int current = stamina.getCurrentStamina();
        int max = stamina.getMaxStamina();
        int cooldown = stamina.getRegenCooldown();
        int regenAmount = stamina.getRegenAmount();
        int upgradeProgressCooldown = stamina.getUpgradeProgressCooldown();

        // If the regeneration cooldown is active, obv don't try to give them more stamina
        if (cooldown > 0) {
            stamina.setRegenCooldown(cooldown - 1);
            shouldSync = true;
        } else if (current < max) {
            // The regeneration rate is dependent on the current exhaustion level of the player
            double regenRate = STAMINA_REGEN_RATE[stamina.getExhaustionLevel()];

            // If not exhausted, just linearly increase the stamina
            if (regenRate >= 1 || player.getRandom().nextFloat() <= regenRate) {
                int newCurrent = Math.min(current + regenAmount, max);
                int exhaustLevel = stamina.getExhaustionLevel();
                int newExhaustionLevel = calculateExhaustionLevel(newCurrent);

                if (newCurrent != current) {
                    stamina.setCurrentStamina(newCurrent);
                    shouldSync = true;
                }

                if (exhaustLevel != newExhaustionLevel) {
                    removeExhaustionEffects(player);
                    stamina.setExhaustionLevel(newExhaustionLevel);
                    shouldSync = true;

                    // Notify the player of a decrease in exhaustion
                    if (newExhaustionLevel < exhaustLevel) {
                        // TODO add in some better indication of the exhaustion changing
                        player.sendSystemMessage(Component.literal("Updated exhaustion"));
                    }
                }
            }
        }

        if (upgradeProgressCooldown > 0) {
            stamina.setUpgradeProgressCooldown(upgradeProgressCooldown - 1);
            shouldSync = true;
        }

        // We re-get this to ensure it is updated to the current stamina value
        int exhaustLevel = stamina.getExhaustionLevel();
        applyExhaustionEffects(player, exhaustLevel);

        if (shouldSync) {
            StaminaSyncEvents.syncNow(player);
        }

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
        boolean shouldSync = false;

        int currentStamina = stamina.getCurrentStamina();
        int exhaustionLevel = stamina.getExhaustionLevel();
        int newCurrent = currentStamina - amount;
        int newExhaustionLevel = calculateExhaustionLevel(newCurrent);

        if (newCurrent != currentStamina) {
            stamina.setCurrentStamina(newCurrent);
            shouldSync = true;
        }

        if (newExhaustionLevel != exhaustionLevel) {
            removeExhaustionEffects(player);
            stamina.setExhaustionLevel(newExhaustionLevel);
            applyExhaustionEffects(player, newExhaustionLevel);
            shouldSync = true;
        }

        int regenCooldown = STAMINA_REGEN_COOLDOWNS[newExhaustionLevel];
        if (stamina.getRegenCooldown() != regenCooldown) {
            stamina.setRegenCooldown(regenCooldown);
            shouldSync = true;
        }

        if (calcUpgradeProgress(player, newCurrent, stamina.getMaxStamina(), stamina.getPointsProgress(), amount, exhaustionLevel, stamina)) {
            shouldSync = true;
        }

        int newUsageTotal = stamina.getUsageTotal() + amount;
        if (stamina.getUsageTotal() != newUsageTotal) {
            stamina.setUsageTotal(newUsageTotal);
            shouldSync = true;
        }

        if (handleMaxStaminaIncrease(player, exhaustionLevel, stamina.getUsageTotal(), stamina)) {
            shouldSync = true;
        }

        // Check to see if the player is at the death level for exhaustion
        if (exhaustionLevel == EXHAUSTION_DEATH_LEVEL) {
            // Always use normal damage application so vanilla death/respawn flow remains intact.
            ModDamageTypes.applyExhaustionDamage(player, Float.MAX_VALUE);
            return;
        } else if (exhaustionLevel > 1) { // Otherwise, see if they just take normal damage
            ModDamageTypes.applyExhaustionDamage(player, EXHAUSTION_DAMAGE_LEVELS[exhaustionLevel]);
        }

        if (shouldSync) {
            StaminaSyncEvents.syncNow(player);
        }
    }

    /**
     * Dedicated helper for restoring stamina outside passive stamina regen ticks.
     * Useful for abilities, items, and scripted recovery effects.
     * @param player
     * @param amount
     */
    public static void restoreStamina(ServerPlayer player, int amount) {
        if (amount <= 0 || !player.isAlive()) return;

        IStaminaData stamina = StaminaAttachments.get(player);
        int current = stamina.getCurrentStamina();
        int max = stamina.getMaxStamina();
        int newCurrent = Math.min(current + amount, max);

        if (newCurrent == current) return;

        int oldExhaustionLevel = stamina.getExhaustionLevel();
        int newExhaustionLevel = calculateExhaustionLevel(newCurrent);

        stamina.setCurrentStamina(newCurrent);
        if (oldExhaustionLevel != newExhaustionLevel) {
            removeExhaustionEffects(player);
            stamina.setExhaustionLevel(newExhaustionLevel);
            applyExhaustionEffects(player, newExhaustionLevel);
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
        int clampedExhaustionLevel = Math.max(0, Math.min(exhaustLevel, EXHAUSTION_LEVELS.length - 1));
        AttributeMap attributes = player.getAttributes();

        Objects.requireNonNull(attributes.getInstance(Attributes.ATTACK_SPEED)).addOrUpdateTransientModifier(EXHAUSTION_ATTACK_SPEED_MODIFIERS[clampedExhaustionLevel]);
        Objects.requireNonNull(attributes.getInstance(Attributes.MOVEMENT_SPEED)).addOrUpdateTransientModifier(EXHAUSTION_MOVE_SPEED_MODIFIERS[clampedExhaustionLevel]);
        Objects.requireNonNull(attributes.getInstance(Attributes.BLOCK_BREAK_SPEED)).addOrUpdateTransientModifier(EXHAUSTION_DIG_SPEED_MODIFIERS[clampedExhaustionLevel]);
        Objects.requireNonNull(attributes.getInstance(Attributes.JUMP_STRENGTH)).addOrUpdateTransientModifier(EXHAUSTION_JUMP_STRENGTH_MODIFIERS[clampedExhaustionLevel]);
        Objects.requireNonNull(attributes.getInstance(Attributes.ATTACK_DAMAGE)).addOrUpdateTransientModifier(EXHAUSTION_ATTACK_DAMAGE_MODIFIERS[clampedExhaustionLevel]);
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
    private static boolean calcUpgradeProgress(ServerPlayer player, int currentStamina, int maxStamina, int curProgress, int staminaUsed, int exhaustLevel, IStaminaData stamina) {
        if (maxStamina <= 0) return false;
        if (staminaUsed <= 0) return false;
        if (stamina.getUpgradeProgressCooldown() > 0) return false;

        // Only award upgrade if they are below a certain percentage of their max stamina
        if (((float) currentStamina / maxStamina) <= STAMINA_GAIN_PERCENTAGE) {
            int progressGain = (int) Math.floor(staminaUsed * EXHAUSTION_MULTIPLIERS[exhaustLevel]);
            int newProgress = progressGain + curProgress;
            boolean changed = false;

            if (newProgress >= UPGRADE_POINT_COST) {
                // Award one point, then start cooldown and clear carryover progress to prevent immediate chaining.
                stamina.setUpgradePoints(stamina.getUpgradePoints() + 1);
                stamina.setPointsProgress(0);
                stamina.setUpgradeProgressCooldown(UPGRADE_PROGRESS_COOLDOWN);
                changed = true;
            } else if (stamina.getPointsProgress() != newProgress) {
                stamina.setPointsProgress(newProgress);
                changed = true;
            }

            return changed;
        }

        return false;
    }

    /**
     * When the player has used more than STAMINA_GAIN_REQ stamina, randomly check to see if they are awarded more
     * stamina.
     * @param player
     * @param exhuastLevel
     * @param newUsageTotal
     * @param stamina
     */
    private static boolean handleMaxStaminaIncrease(ServerPlayer player, int exhuastLevel, int newUsageTotal, IStaminaData stamina) {
        double chanceToIncrease = exhuastLevel > 0 ? STAMINA_GAIN_EXHAUSTED_CHANCE : STAMINA_GAIN_CHANCE;

        if (newUsageTotal >= STAMINA_GAIN_REQ && player.getRandom().nextFloat() < chanceToIncrease) {
            int maxIncrease = (int) Math.floor(player.getRandom().nextFloat() * STAMINA_MAX_INCREASE) + 1;
            stamina.setMaxStamina(stamina.getMaxStamina() + maxIncrease);
            stamina.setUsageTotal(0);
            return true;
        }

        return false;
    }

    /**
     * This function resets all the stamina values to their baseline values, primarily used when a player dies
     * @param player
     */
    public static void handlePlayerDeath(ServerPlayer player) {
        IStaminaData stamina = StaminaAttachments.get(player);
        removeExhaustionEffects(player);

        stamina.setCurrentStamina(stamina.getMaxStamina());
        stamina.setRegenCooldown(0);
        stamina.setExhaustionLevel(0);
        stamina.setLastHurrahUsed(false);
        stamina.setUsageTotal(0);
        stamina.setPointsProgress(0);
        stamina.setPowersDisabled(false);
        StaminaSyncEvents.syncNow(player);
    }

    private static AttributeModifier[] createExhaustionModifiers(Identifier modifierId, double[] values) {
        AttributeModifier[] modifiers = new AttributeModifier[values.length];
        for (int i = 0; i < values.length; i++) {
            modifiers[i] = new AttributeModifier(modifierId, values[i], AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        }
        return modifiers;
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
            case "currentStamina", "current" -> stamina.setCurrentStamina(amount);
            case "maxStamina", "max" -> stamina.setMaxStamina(amount);
            case "usageTotal" -> stamina.setUsageTotal(amount);
            case "regenCooldown" -> stamina.setRegenCooldown(amount);
            case "regenAmount" -> stamina.setRegenAmount(amount);
            case "exhaustionLevel" -> stamina.setExhaustionLevel(amount);
            case "upgradePoints" -> stamina.setUpgradePoints(amount);
            case "pointsProgress" -> stamina.setPointsProgress(amount);
            case "upgradeProgressCooldown" -> stamina.setUpgradeProgressCooldown(amount);
        }

        StaminaSyncEvents.syncNow(player);
    }

    public static void forceSetStaminaData(ServerPlayer player, boolean value, String dataName) {
        IStaminaData stamina = StaminaAttachments.get(player);

        switch (dataName) {
            case "lastHurrahUsed" -> stamina.setLastHurrahUsed(value);
            case "powersDisabled" -> stamina.setPowersDisabled(value);
            case "initialized" -> stamina.setInitialized(value);
        }

        StaminaSyncEvents.syncNow(player);
    }

}