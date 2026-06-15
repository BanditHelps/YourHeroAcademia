package com.github.bandithelps.client.blackwhip;

import net.minecraft.world.phys.Vec3;

/**
 * Client-side snapshot of the local player's active swing anchor, pushed by the server when a
 * {@code blackwhip_zip} swing starts/stops. The {@code BlackwhipSwingController} reads this each tick
 * to run the pendulum simulation.
 */
public final class ClientBlackwhipSwingState {
    private static volatile boolean active = false;
    private static volatile double anchorX;
    private static volatile double anchorY;
    private static volatile double anchorZ;
    private static volatile double ropeLength;

    private ClientBlackwhipSwingState() {
    }

    public static void set(boolean isActive, double x, double y, double z, double length) {
        active = isActive;
        anchorX = x;
        anchorY = y;
        anchorZ = z;
        ropeLength = length;
    }

    public static void clear() {
        active = false;
    }

    public static boolean isActive() {
        return active;
    }

    public static Vec3 getAnchor() {
        return new Vec3(anchorX, anchorY, anchorZ);
    }

    public static double getRopeLength() {
        return ropeLength;
    }

    public static void setRopeLength(double length) {
        ropeLength = length;
    }
}
