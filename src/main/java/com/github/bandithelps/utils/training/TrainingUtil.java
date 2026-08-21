package com.github.bandithelps.utils.training;

import com.github.bandithelps.capabilities.body.BodyAttachments;
import com.github.bandithelps.capabilities.body.BodyPart;
import com.github.bandithelps.capabilities.body.BodySyncEvents;
import com.github.bandithelps.capabilities.body.IBodyData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;

/**
 * This class handles things related to the physical training of each of the players.
 * Checks statistics to update the max values, all of which are configurable.
 * We do keep track here of "progress" towards the threshold using persistent data.
 * This is needed to help prevent "rollbacks" if thresholds are changed in the config files after a server has already been running
 */
public final class TrainingUtil {
    private static final float DEFAULT_MAX_VALUE = 5.0F;
    private static final float HARD_MAX_CAP = 50.0F;

    private static final int SPEED_MAX_THRESHOLD = 255_000;
    private static final int STRENGTH_MAX_THRESHOLD = 20_000;
    private static final int ARMOR_MAX_THRESHOLD = 4_500;
    private static final int TOUGHNESS_MAX_THRESHOLD = 4_000;
    private static final int HEALTH_MAX_THRESHOLD = 8_000;

    private static final String TRAINING_PREFIX = "YhaTraining.";

    private TrainingUtil() {
    }

    public static void updateResults(ServerPlayer player) {
        IBodyData body = BodyAttachments.get(player);
        boolean changed = false;

        // Pull cumulative vanilla stat totals once, then convert to deltas in tryUpgradeMax.
        int sprintDistance = player.getStats().getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM));
        int damageDealt = player.getStats().getValue(Stats.CUSTOM.get(Stats.DAMAGE_DEALT));
        int damageTaken = player.getStats().getValue(Stats.CUSTOM.get(Stats.DAMAGE_TAKEN));
        int damageResisted = player.getStats().getValue(Stats.CUSTOM.get(Stats.DAMAGE_RESISTED));
        int damageAbsorbed = player.getStats().getValue(Stats.CUSTOM.get(Stats.DAMAGE_ABSORBED));
        int toughnessTraining = safeAdd(damageResisted, damageAbsorbed);

        changed |= tryUpgradeMax(player, body, "speed", sprintDistance, SPEED_MAX_THRESHOLD, "speed_max");
        changed |= tryUpgradeMax(player, body, "strength", damageDealt, STRENGTH_MAX_THRESHOLD, "strength_max");
        changed |= tryUpgradeMax(player, body, "armor", damageTaken, ARMOR_MAX_THRESHOLD, "armor_max");
        changed |= tryUpgradeMax(player, body, "max_health", damageTaken, HEALTH_MAX_THRESHOLD, "max_health_max");
        changed |= tryUpgradeMax(player, body, "armor_toughness", toughnessTraining, TOUGHNESS_MAX_THRESHOLD, "armor_toughness_max");

        // Only sync body data if at least one max value changed this pass.
        if (changed) {
            BodySyncEvents.syncNow(player);
        }
    }

    /**
     * Here, we attempt to increment the max values if the players have reached the certain threshold.
     * If they haven't store the persistent count in nbt data s
     * @param player
     * @param body
     * @param metricId
     * @param currentTotal
     * @param threshold
     * @param maxKey
     * @return
     */
    private static boolean tryUpgradeMax(
            ServerPlayer player,
            IBodyData body,
            String metricId,
            int currentTotal,
            int threshold,
            String maxKey
    ) {
        if (threshold <= 0) {
            return false;
        }

        String initializedKey = TRAINING_PREFIX + metricId + ".initialized";
        String lastSeenKey = TRAINING_PREFIX + metricId + ".last_seen";
        String progressKey = TRAINING_PREFIX + metricId + ".progress";

        // First run stores a baseline so existing lifetime stats do not instantly grant levels.
        if (!player.getPersistentData().getBoolean(initializedKey).orElse(false)) {
            player.getPersistentData().putBoolean(initializedKey, true);
            player.getPersistentData().putInt(lastSeenKey, currentTotal);
            player.getPersistentData().putInt(progressKey, 0);
            return false;
        }

        int previousTotal = player.getPersistentData().getInt(lastSeenKey).orElse(currentTotal);
        player.getPersistentData().putInt(lastSeenKey, currentTotal);

        int delta = currentTotal - previousTotal;
        if (delta <= 0) {
            // Stat resets or rollbacks invalidate progress for this metric.
            if (delta < 0) {
                player.getPersistentData().putInt(progressKey, 0);
            }
            return false;
        }

        float currentMax = body.getCustomFloat(player, BodyPart.CHEST, maxKey, DEFAULT_MAX_VALUE);
        if (currentMax >= HARD_MAX_CAP) {
            player.getPersistentData().putInt(progressKey, 0);
            return false;
        }

        int totalProgress = player.getPersistentData().getInt(progressKey).orElse(0) + delta;
        int levelGainsAvailable = totalProgress / threshold;
        if (levelGainsAvailable <= 0) {
            player.getPersistentData().putInt(progressKey, totalProgress);
            return false;
        }

        float targetMax = Math.min(HARD_MAX_CAP, currentMax + levelGainsAvailable);
        int levelsApplied = (int) (targetMax - currentMax);
        if (levelsApplied <= 0) {
            player.getPersistentData().putInt(progressKey, 0);
            return false;
        }

        int remainingProgress = totalProgress - (levelsApplied * threshold);
        if (targetMax >= HARD_MAX_CAP) {
            // Once capped, discard extra progress so we do not store unbounded leftovers.
            remainingProgress = 0;
        }
        player.getPersistentData().putInt(progressKey, Math.max(0, remainingProgress));
        body.setCustomFloat(player, BodyPart.CHEST, maxKey, targetMax);
        return true;
    }

    /*
     * Addition function that doesn't go over the max or min int values
     */
    private static int safeAdd(int a, int b) {
        long sum = (long) a + b;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (sum < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) sum;
    }
}
