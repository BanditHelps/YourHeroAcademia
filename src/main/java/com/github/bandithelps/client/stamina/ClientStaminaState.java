package com.github.bandithelps.client.stamina;

public final class ClientStaminaState {
    private static volatile int currentStamina = 100;
    private static volatile int maxStamina = 100;

    private ClientStaminaState() {
    }

    public static int getCurrentStamina() {
        return currentStamina;
    }

    public static int getMaxStamina() {
        return maxStamina;
    }

    public static void set(int current, int max) {
        maxStamina = Math.max(1, max);
        currentStamina = Math.min(current, maxStamina);
    }
}
