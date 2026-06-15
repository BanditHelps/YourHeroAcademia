package com.github.bandithelps.client.blackwhip;

/**
 * Client-side state for the "break free" struggle minigame, mirrored from the server. Drives the
 * struggle HUD overlay and gates the client tap input.
 */
public final class ClientBlackwhipStruggleState {
    private static volatile boolean active = false;
    private static volatile int taps = 0;
    private static volatile int threshold = 1;

    private ClientBlackwhipStruggleState() {
    }

    public static void set(boolean isActive, int currentTaps, int requiredTaps) {
        active = isActive;
        taps = currentTaps;
        threshold = Math.max(1, requiredTaps);
    }

    public static boolean isActive() {
        return active;
    }

    public static int getTaps() {
        return taps;
    }

    public static int getThreshold() {
        return threshold;
    }

    public static float getProgress() {
        return Math.min(1.0f, taps / (float) threshold);
    }
}
