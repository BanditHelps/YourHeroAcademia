package com.github.bandithelps.client.blackwhip;

/** Client-side Puppet hold session (scroll capture gate for tether reel). */
public final class ClientBlackwhipChainReelState {

    private static boolean active;
    private static String mode = "";

    private ClientBlackwhipChainReelState() {
    }

    public static void start(String sessionMode) {
        active = true;
        mode = sessionMode == null ? "" : sessionMode;
    }

    public static void stop() {
        active = false;
        mode = "";
    }

    public static boolean isActive() {
        return active;
    }

    public static String getMode() {
        return mode;
    }
}
