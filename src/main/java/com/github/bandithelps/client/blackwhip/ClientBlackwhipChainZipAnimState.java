package com.github.bandithelps.client.blackwhip;

/**
 * Client snapshot for Blackwhip chain zip player animations (reel wind-up / punch).
 */
public final class ClientBlackwhipChainZipAnimState {

    public enum Phase {
        NONE,
        REEL,
        PUNCH
    }

    private static volatile Phase phase = Phase.NONE;
    /** Bumped on each punch so the controller can force-replay the one-shot. */
    private static int punchToken;

    private ClientBlackwhipChainZipAnimState() {
    }

    public static void setPhase(Phase next) {
        if (next == null) {
            next = Phase.NONE;
        }
        if (next == Phase.PUNCH) {
            punchToken++;
        }
        phase = next;
    }

    public static void clear() {
        phase = Phase.NONE;
    }

    public static Phase getPhase() {
        return phase;
    }

    public static int getPunchToken() {
        return punchToken;
    }

    public static boolean isActive() {
        return phase != Phase.NONE;
    }
}
