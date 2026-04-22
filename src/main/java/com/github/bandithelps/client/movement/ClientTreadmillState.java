package com.github.bandithelps.client.movement;

public final class ClientTreadmillState {
    private static volatile boolean mounted;

    private ClientTreadmillState() {
    }

    public static boolean isMounted() {
        return mounted;
    }

    public static void setMounted(boolean treadmillMounted) {
        mounted = treadmillMounted;
    }
}
