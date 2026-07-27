package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.network.BlackwhipChainSwingPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Client snapshot for the local player's chain Blackwhip swing (input-driven pendulum).
 */
public final class ClientBlackwhipChainSwingState {
    private static volatile boolean active = false;
    private static volatile double anchorX;
    private static volatile double anchorY;
    private static volatile double anchorZ;
    private static volatile double ropeLength = 8.0;
    private static volatile double minRope = 2.0;
    private static volatile double maxRope = 64.0;
    private static volatile double reelSpeed = 0.3;
    private static volatile double pumpAccel = 0.025;
    private static volatile double damping = 0.992;
    private static volatile double maxSpeed = 2.4;

    private ClientBlackwhipChainSwingState() {
    }

    public static void apply(BlackwhipChainSwingPayload payload) {
        active = payload.active();
        if (!payload.active()) {
            return;
        }
        anchorX = payload.anchorX();
        anchorY = payload.anchorY();
        anchorZ = payload.anchorZ();
        ropeLength = payload.ropeLength();
        minRope = Math.max(0.5, payload.minRope());
        maxRope = Math.max(minRope, payload.maxRope());
        reelSpeed = Math.max(0.01, payload.reelSpeed());
        pumpAccel = Math.max(0.0, payload.pumpAccel());
        damping = payload.damping() <= 0.0f ? 0.997 : payload.damping();
        maxSpeed = Math.max(0.5, payload.maxSpeed());
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

    public static double getMinRope() {
        return minRope;
    }

    public static double getMaxRope() {
        return maxRope;
    }

    public static double getReelSpeed() {
        return reelSpeed;
    }

    public static double getPumpAccel() {
        return pumpAccel;
    }

    public static double getDamping() {
        return damping;
    }

    public static double getMaxSpeed() {
        return maxSpeed;
    }
}
