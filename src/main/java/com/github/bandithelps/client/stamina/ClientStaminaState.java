package com.github.bandithelps.client.stamina;

public final class ClientStaminaState {
    private static volatile int currentStamina = 100;
    private static volatile int maxStamina = 100;
    private static volatile int usageTotal;
    private static volatile int regenCooldown;
    private static volatile int regenAmount = 1;
    private static volatile int exhaustionLevel;
    private static volatile boolean lastHurrahUsed;
    private static volatile boolean powersDisabled;
    private static volatile boolean initialized;
    private static volatile int upgradePoints;
    private static volatile int pointsProgress;
    private static volatile boolean debugOverlayEnabled;

    private ClientStaminaState() {
    }

    public static int getCurrentStamina() {
        return currentStamina;
    }

    public static int getMaxStamina() {
        return maxStamina;
    }

    public static int getUsageTotal() {
        return usageTotal;
    }

    public static int getRegenCooldown() {
        return regenCooldown;
    }

    public static int getRegenAmount() {
        return regenAmount;
    }

    public static int getExhaustionLevel() {
        return exhaustionLevel;
    }

    public static boolean getLastHurrahUsed() {
        return lastHurrahUsed;
    }

    public static boolean isPowersDisabled() {
        return powersDisabled;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    public static int getUpgradePoints() {
        return upgradePoints;
    }

    public static int getPointsProgress() {
        return pointsProgress;
    }

    public static boolean isDebugOverlayEnabled() {
        return debugOverlayEnabled;
    }

    public static void setDebugOverlayEnabled(boolean enabled) {
        debugOverlayEnabled = enabled;
    }

    public static void set(
            int current,
            int max,
            int usage,
            int cooldown,
            int regen,
            int exhaustion,
            boolean hurrahUsed,
            boolean disabled,
            boolean hasInitialized,
            int points,
            int progress
    ) {
        maxStamina = Math.max(1, max);
        currentStamina = Math.min(current, maxStamina);
        usageTotal = Math.max(0, usage);
        regenCooldown = Math.max(0, cooldown);
        regenAmount = Math.max(0, regen);
        exhaustionLevel = Math.max(0, exhaustion);
        lastHurrahUsed = hurrahUsed;
        powersDisabled = disabled;
        initialized = hasInitialized;
        upgradePoints = Math.max(0, points);
        pointsProgress = Math.max(0, progress);
    }
}
