package com.github.bandithelps.client.blackwhip;

import com.github.bandithelps.network.BlackwhipWebSwingPayload;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Client snapshot for the local player's PS5-style web swing.
 */
public final class ClientBlackwhipWebSwingState {
    /** Ignore apex checks for a few ticks so ground takeoff / attach don't instantly snap. */
    private static final int ARC_GRACE_TICKS = 10;
    /** Hard cap so a stalled / weird swing can't be held indefinitely. */
    private static final int MAX_SWING_TICKS = 50;
    /** Must dip this low (0 = bottom, 1 = top) before max-arc break can trigger. */
    private static final double BOTTOM_ARC_THRESHOLD = 0.42;
    /** Relative dip from the highest point seen counts as entering the swing. */
    private static final double DIP_FOR_SWING = 0.22;
    /** Crest band where the whip can snap. */
    private static final double MAX_ARC_THRESHOLD = 0.68;
    private static final double HIGH_ARC_THRESHOLD = 0.62;

    public enum BreakReason {
        NONE,
        APEX,
        TIMEOUT
    }

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
    private static int swingTicks;
    private static boolean sawBottom;
    private static double maxHeightSeen;
    private static double prevVy;
    private static boolean breakRequested;

    private ClientBlackwhipWebSwingState() {
    }

    public static void apply(BlackwhipWebSwingPayload payload) {
        active = payload.active();
        if (!payload.active()) {
            resetArcTracker();
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
        resetArcTracker();
    }

    public static void clear() {
        active = false;
        resetArcTracker();
    }

    private static void resetArcTracker() {
        swingTicks = 0;
        sawBottom = false;
        maxHeightSeen = 0.0;
        prevVy = 0.0;
        breakRequested = false;
    }

    /**
     * Tracks swing progress and returns a break reason once when the whip should snap.
     */
    public static BreakReason updateAndShouldBreak(LocalPlayer player, Vec3 velocity) {
        if (!active || breakRequested || player == null || velocity == null) {
            return BreakReason.NONE;
        }
        swingTicks++;

        Vec3 center = player.position().add(0.0, player.getBbHeight() * 0.5, 0.0);
        double below = Math.max(0.0, anchorY - center.y);
        double maxBelow = Math.max(ropeLength * 0.95, 1.0);
        double heightAlongArc = 1.0 - Mth.clamp(below / maxBelow, 0.0, 1.0);
        maxHeightSeen = Math.max(maxHeightSeen, heightAlongArc);

        if (heightAlongArc < BOTTOM_ARC_THRESHOLD || maxHeightSeen - heightAlongArc >= DIP_FOR_SWING) {
            sawBottom = true;
        }

        BreakReason reason = BreakReason.NONE;
        if (swingTicks >= MAX_SWING_TICKS) {
            reason = BreakReason.TIMEOUT;
        } else if (swingTicks >= ARC_GRACE_TICKS && sawBottom && !player.onGround()) {
            if (heightAlongArc >= MAX_ARC_THRESHOLD) {
                // Classic crest: was rising, now stalling / tipping over.
                if (prevVy > 0.06 && velocity.y <= 0.04) {
                    reason = BreakReason.APEX;
                } else if (heightAlongArc >= HIGH_ARC_THRESHOLD && velocity.y < 0.15) {
                    // Already very high and no longer climbing hard.
                    reason = BreakReason.APEX;
                }
            }
        }

        prevVy = velocity.y;
        if (reason != BreakReason.NONE) {
            breakRequested = true;
            return reason;
        }
        return BreakReason.NONE;
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
