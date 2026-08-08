package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.network.BlackwhipWebSwingPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Client snapshot for the local player's PS5-style web swing.
 */
public final class ClientBlackwhipWebSwingState {
    private static volatile boolean active = false;
    private static volatile double anchorX;
    private static volatile double anchorY;
    private static volatile double anchorZ;
    private static volatile double ropeLength = 12.0;
    private static volatile double minRope = 3.0;
    private static volatile double maxRope = 48.0;
    private static volatile double pumpAccel = 0.065;
    private static volatile double turnAssist = 0.045;
    private static volatile double autoReelRate = 0.08;
    private static volatile double damping = 0.996;
    private static volatile double maxSpeed = 3.2;
    private static volatile double brakeDamp = 0.88;
    private static volatile float lastYaw;

    private ClientBlackwhipWebSwingState() {
    }

    public static void apply(BlackwhipWebSwingPayload payload) {
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
        pumpAccel = Math.max(0.0, payload.pumpAccel());
        turnAssist = Math.max(0.0, payload.turnAssist());
        autoReelRate = Math.max(0.0, payload.autoReelRate());
        damping = payload.damping() <= 0.0f ? 0.996 : payload.damping();
        maxSpeed = Math.max(0.5, payload.maxSpeed());
        float brake = payload.brakeDamp();
        brakeDamp = brake <= 0.0f ? 0.88 : Math.max(0.5f, Math.min(0.99f, brake));
        lastYaw = Float.NaN;
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

    public static double getPumpAccel() {
        return pumpAccel;
    }

    public static double getTurnAssist() {
        return turnAssist;
    }

    public static double getAutoReelRate() {
        return autoReelRate;
    }

    public static double getDamping() {
        return damping;
    }

    public static double getMaxSpeed() {
        return maxSpeed;
    }

    public static double getBrakeDamp() {
        return brakeDamp;
    }

    public static float getLastYaw() {
        return lastYaw;
    }

    public static void setLastYaw(float yaw) {
        lastYaw = yaw;
    }
}
