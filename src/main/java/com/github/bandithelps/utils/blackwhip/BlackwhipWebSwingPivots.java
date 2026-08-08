package com.github.bandithelps.utils.blackwhip;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves web-swing pivots using Palladium 26.1 {@code SwingingFlightType.Controller#findNewAnchor}
 * logic: place an ideal forward-up point from swing level + radius, fan raycasts for a real block
 * near that ideal, and fall back to the ideal as a virtual air pivot when nothing solid is hit.
 */
public final class BlackwhipWebSwingPivots {

    /**
     * Unit-direction samples matching Palladium's elevation fan (~45° / 55° / 65° / 75°).
     * Pair index {@code i} uses {@link #UP_COMP}[i] as Y and {@link #HORIZ_COMP}[i] as XZ scale.
     */
    private static final double[] UP_COMP = {0.707d, 0.819d, 0.906d, 0.966d};
    private static final double[] HORIZ_COMP = {0.707d, 0.574d, 0.423d, 0.259d};
    /** Yaw offsets in 20° steps: 0°, ±20°, ±40° (Palladium order). */
    private static final int[] YAW_UNITS = {0, -1, 1, -2, 2};
    private static final double YAW_STEP_DEG = 20.0d;
    /** Vertical weight when scoring block hits vs the ideal pivot (Palladium uses 5×). */
    private static final double VERT_SCORE_WEIGHT = 5.0d;
    private static final double MIN_COS_ELEV = 0.65d;
    private static final double MAX_COS_ELEV = 0.98d;
    private static final double GROUND_CLEARANCE = 2.5d;

    public record Pivot(Vec3 point, BlockPos support, boolean virtual) {
    }

    private BlackwhipWebSwingPivots() {
    }

    /**
     * @param range    max ray / virtual reach
     * @param minDist  minimum ideal pivot distance
     * @param maxDist  maximum ideal pivot distance (clamped by range)
     * @param elevBias extra elevation toward steeper ideal aim (0–1); mirrors Palladium's
     *                 near-ground steepening slightly when already low
     */
    public static Pivot resolve(ServerLevel level, Player player, double range,
                                double minDist, double maxDist, double elevBias) {
        Vec3 eye = player.getEyePosition();
        Vec3 forward = horizontal(player.getLookAngle());
        if (forward.lengthSqr() < 1.0e-4) {
            forward = horizontal(player.getDeltaMovement());
        }
        if (forward.lengthSqr() < 1.0e-4) {
            // No usable heading — still allow a virtual pivot ahead of body yaw.
            float yawRad = player.getYRot() * ((float) Math.PI / 180.0f);
            forward = new Vec3(-Mth.sin(yawRad), 0.0, Mth.cos(yawRad));
        }
        forward = forward.normalize();

        // Blend travel heading so reattach continues the line of motion (YHA addition).
        Vec3 vel = player.getDeltaMovement();
        Vec3 flatVel = new Vec3(vel.x, 0.0, vel.z);
        double horizSpeed = flatVel.length();
        if (horizSpeed > 0.08) {
            forward = normalizeSafe(forward.scale(0.7).add(flatVel.normalize().scale(0.3)));
        }

        double radius = Mth.lerp(Mth.clamp(horizSpeed / 1.8, 0.0, 1.0), minDist, Math.min(maxDist, range));
        radius = Mth.clamp(radius, minDist, Math.min(maxDist, range));

        // First attach uses player Y as Palladium's swingLevel.
        double swingLevel = player.getY();
        Vec3 ideal = computeIdealPivot(level, player, eye, forward, radius, swingLevel, elevBias, range);
        if (ideal.y <= player.getY() + 1.0) {
            // Degenerate ideal — keep a usable forward-up virtual so deploy never no-ops.
            return new Pivot(ideal, BlockPos.containing(ideal), true);
        }

        Pivot block = findBestBlockNearIdeal(level, player, eye, forward, radius, ideal, swingLevel);
        if (block != null) {
            return block;
        }
        return new Pivot(ideal, BlockPos.containing(ideal), true);
    }

    /**
     * Palladium ideal air pivot: elevates with radius, steepens near the ground
     * ({@code cosElev} clamped to 0.65–0.98), then clamps Y by reach.
     */
    private static Vec3 computeIdealPivot(ServerLevel level, Player player, Vec3 eye, Vec3 forward,
                                          double radius, double swingLevel, double elevBias, double range) {
        int gx = Mth.floor(player.getX());
        int gz = Mth.floor(player.getZ());
        double groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, gx, gz);
        double heightAboveGround = swingLevel - groundY - GROUND_CLEARANCE;
        double t = 1.0 - heightAboveGround / Math.max(1.0e-3, radius);
        double cosElev = Mth.clamp(Math.max(MIN_COS_ELEV, t), MIN_COS_ELEV, MAX_COS_ELEV);
        // elevBias steepens slightly within Palladium's clamp band.
        cosElev = Mth.clamp(cosElev + Mth.clamp(elevBias, 0.0, 1.0) * 0.06, MIN_COS_ELEV, MAX_COS_ELEV);
        double sinElev = Math.sqrt(Math.max(0.0, 1.0 - cosElev * cosElev));

        double idealY = swingLevel + player.getEyeHeight() + radius * cosElev;
        Vec3 ideal = new Vec3(
                eye.x + forward.x * radius * sinElev,
                idealY,
                eye.z + forward.z * radius * sinElev);
        return clampReachHeight(eye, ideal, range);
    }

    /**
     * Fan of rays at Palladium elevations/yaws; pick the block hit closest to {@code ideal}
     * using score = horizDist + 5 * |dy|.
     */
    private static Pivot findBestBlockNearIdeal(ServerLevel level, Player player, Vec3 eye,
                                               Vec3 forward, double radius, Vec3 ideal,
                                               double swingLevel) {
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        double minHitY = swingLevel + radius * 0.5;

        Vec3 bestPoint = null;
        BlockPos bestSupport = null;
        double bestScore = Double.POSITIVE_INFINITY;

        for (int i = 0; i < UP_COMP.length; i++) {
            double up = UP_COMP[i];
            double horiz = HORIZ_COMP[i];
            for (int yawUnit : YAW_UNITS) {
                double yawRad = Math.toRadians(yawUnit * YAW_STEP_DEG);
                Vec3 dirHoriz = forward.scale(Math.cos(yawRad)).add(right.scale(Math.sin(yawRad)));
                Vec3 dir = normalizeSafe(dirHoriz.scale(horiz).add(0.0, up, 0.0));

                Vec3 target = clampReachHeight(eye, eye.add(dir.scale(radius)), radius);
                if (target.y <= minHitY) {
                    continue;
                }

                BlockHitResult hit = level.clip(new ClipContext(
                        eye, target,
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                if (hit.getType() != HitResult.Type.BLOCK) {
                    continue;
                }

                Vec3 loc = hit.getLocation();
                if (loc.y <= minHitY) {
                    continue;
                }

                double dx = loc.x - ideal.x;
                double dz = loc.z - ideal.z;
                double horizDist = Math.sqrt(dx * dx + dz * dz);
                double vertDist = Math.abs(loc.y - ideal.y);
                double score = horizDist + vertDist * VERT_SCORE_WEIGHT;
                if (score < bestScore) {
                    bestScore = score;
                    // Face-nudge so the chain tip sits on the surface (YHA latch convention).
                    bestPoint = BlackwhipChainAnchors.surfaceAttachPoint(hit);
                    bestSupport = hit.getBlockPos();
                }
            }
        }

        if (bestPoint == null || bestSupport == null) {
            return null;
        }
        return new Pivot(bestPoint, bestSupport, false);
    }

    /** Soft max-height clamp analogous to Palladium's absolute max, using eye + reach. */
    private static Vec3 clampReachHeight(Vec3 eye, Vec3 point, double range) {
        double maxY = eye.y + range;
        if (point.y <= maxY) {
            return point;
        }
        return new Vec3(point.x, maxY, point.z);
    }

    private static Vec3 horizontal(Vec3 v) {
        return new Vec3(v.x, 0.0, v.z);
    }

    private static Vec3 normalizeSafe(Vec3 v) {
        if (v.lengthSqr() < 1.0e-8) {
            return new Vec3(0.0, 0.0, 1.0);
        }
        return v.normalize();
    }
}
