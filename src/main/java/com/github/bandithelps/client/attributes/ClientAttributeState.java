package com.github.bandithelps.client.attributes;

/**
 * This client state syncs the attributes that for some reason are not available to the local player.
 */
public final class ClientAttributeState {
    private static volatile double attackDamage = 1.0D;
    private static volatile boolean attackDamageInitialized;

    private ClientAttributeState() {
    }

    public static double getAttackDamage() {
        return attackDamage;
    }

    public static boolean isAttackDamageInitialized() {
        return attackDamageInitialized;
    }

    public static void setAttackDamage(double value) {
        if (!Double.isFinite(value)) {
            return;
        }
        attackDamage = value;
        attackDamageInitialized = true;
    }
}
