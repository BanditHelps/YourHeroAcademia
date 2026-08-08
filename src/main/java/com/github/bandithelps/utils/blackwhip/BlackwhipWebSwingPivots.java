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
 * logic (widened for side cliffs): place an ideal forward-up point, fan raycasts for a real block
 * ahead/above/to-the-side, and fall back to the ideal as a virtual air pivot when nothing hits.
 */
public final class BlackwhipWebSwingPivots {

    /**
     * Elevation fan: Palladium's steep samples plus flatter ones for cliff/mountain faces
     * (~30° / 40° / 45° / 55° / 65° / 75°). Index {@code i} pairs {@link #UP_COMP} with
     * {@link #HORIZ_COMP}.
     */
    private static final double[] UP_COMP = {0.500d, 0.643d, 0.707d, 0.819d, 0.906d, 0.966d};
    private static final double[] HORIZ_COMP = {0.866d, 0.766d, 0.707d, 0.574d, 0.423d, 0.259d};
    /** Yaw offsets in 20° steps out to ±80° so side terrain (mountain on the right) is in fan. */
    private static final int[] YAW_UNITS = {0, -1, 1, -2, 2, -3, 3, -4, 4};
    private static final double YAW_STEP_DEG = 20.0d;
    /**
     * Vertical weight vs the ideal pivot. Palladium uses 5× (strongly prefers overhead matches);
     * keep this milder so side cliffs below the ideal still win over air.
     */
    private static final double VERT_SCORE_WEIGHT = 1.6d;
    /** Mild penalty for hits that sit far off the look/travel heading (still allow ~side grabs). */
    private static final double SIDE_SCORE_WEIGHT = 0.35d;
    /** Reject hits behind the player (dot of flat eye→hit with forward). */
    private static final double MIN_FORWARD_DOT = -0.05d;
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
     * Fan of rays at elevations/yaws (wider than Palladium for side cliffs); pick the best block
     * hit near {@code ideal} with a mild forward preference.
     */
    private static Pivot findBestBlockNearIdeal(ServerLevel level, Player player, Vec3 eye,
                                               Vec3 forward, double radius, Vec3 ideal,
                                               double swingLevel) {
        Vec3 right = new Vec3(-forward.z, 0.0, forward.x);
        // Allow mid-height cliff faces; Palladium's radius*0.5 was rejecting most side terrain.
        double minHitY = swingLevel + Math.max(2.5d, radius * 0.22d);

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

                Vec3 flatTo = horizontal(loc.subtract(eye));
                double flatLen = flatTo.length();
                if (flatLen < 1.5d) {
                    // Too close horizontally — underfoot / directly overhead noise.
                    continue;
                }
                double forwardDot = flatTo.scale(1.0d / flatLen).dot(forward);
                if (forwardDot < MIN_FORWARD_DOT) {
                    continue;
                }

                double dx = loc.x - ideal.x;
                double dz = loc.z - ideal.z;
                double horizDist = Math.sqrt(dx * dx + dz * dz);
                double vertDist = Math.abs(loc.y - ideal.y);
                // Side hits: mild heading penalty instead of Palladium's hard overhead bias.
                double sidePenalty = (1.0d - Mth.clamp(forwardDot, 0.0d, 1.0d)) * radius * SIDE_SCORE_WEIGHT;
                double score = horizDist + vertDist * VERT_SCORE_WEIGHT + sidePenalty;
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
