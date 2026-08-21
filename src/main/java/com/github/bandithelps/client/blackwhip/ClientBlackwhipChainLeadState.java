package com.github.bandithelps.client.blackwhip;

/** Client-side mirror of whether Lead is toggled on for the local player. */
public final class ClientBlackwhipChainLeadState {

    private static boolean active;

    private ClientBlackwhipChainLeadState() {
    }

    public static void setActive(boolean value) {
        active = value;
    }

    public static boolean isActive() {
        return active;
    }
}
