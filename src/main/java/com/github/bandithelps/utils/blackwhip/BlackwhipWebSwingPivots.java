package com.github.bandithelps.utils.blackwhip;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Resolves PS5-style web-swing pivots: prefer a real surface ahead-and-above, otherwise place a
 * virtual air point on a forward-up diagonal (never straight overhead).
 */
public final class BlackwhipWebSwingPivots {

    /** Preferred elevation above the horizon for virtual pivots (degrees). */
    private static final double TARGET_ELEV_DEG = 32.0;
    /** Clamp look pitch contribution so aim cannot go straight up. */
    private static final double MAX_AIM_PITCH_DEG = 48.0;
    private static final double MIN_AIM_PITCH_DEG = 22.0;

    public record Pivot(Vec3 point, BlockPos support, boolean virtual) {
    }

    private BlackwhipWebSwingPivots() {
    }

    /**
     * @param range      max ray / virtual reach
     * @param minDist    minimum virtual pivot distance
     * @param maxDist    maximum virtual pivot distance (clamped by range)
     * @param elevBias   extra elevation blend (0–1) toward steeper forward-up aim
     */
    public static Pivot resolve(ServerLevel level, Player player, double range,
                                double minDist, double maxDist, double elevBias) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        if (look.lengthSqr() < 1.0e-6) {
            look = new Vec3(0.0, 0.0, 1.0);
        } else {
            look = look.normalize();
        }

        Pivot surface = findBestSurface(level, player, eye, look, range);
        if (surface != null) {
            return surface;
        }
        return virtualPivot(player, eye, look, range, minDist, maxDist, elevBias);
    }

    private static Pivot findBestSurface(ServerLevel level, Player player, Vec3 eye, Vec3 look, double range) {
        Vec3 flatLook = flatNormalize(look);
        Vec3 up = new Vec3(0.0, 1.0, 0.0);
        Vec3 right = flatLook.cross(up).normalize();

        // Prefer forward-up samples; avoid near-vertical rays.
        Vec3[] dirs = new Vec3[]{
                aimAtPitch(flatLook, 28.0),
                aimAtPitch(flatLook, 34.0),
                aimAtPitch(flatLook, 24.0),
                normalizeSafe(aimAtPitch(flatLook, 32.0).add(right.scale(0.22))),
                normalizeSafe(aimAtPitch(flatLook, 32.0).add(right.scale(-0.22))),
                normalizeSafe(aimAtPitch(flatLook, 38.0).add(right.scale(0.18))),
                normalizeSafe(aimAtPitch(flatLook, 38.0).add(right.scale(-0.18))),
                // Mild look blend only if the player is already aiming ahead.
                look.y < 0.75 ? look : aimAtPitch(flatLook, 34.0)
        };

        double bodyY = player.getY() + player.getBbHeight() * 0.55;
        Pivot best = null;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (Vec3 dir : dirs) {
            BlockHitResult hit = level.clip(new ClipContext(
                    eye, eye.add(dir.scale(range)),
                    ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
            if (hit.getType() != HitResult.Type.BLOCK) {
                continue;
            }
            Vec3 point = BlackwhipChainAnchors.surfaceAttachPoint(hit);
            Vec3 toPoint = point.subtract(eye);
            double forward = flatNormalize(toPoint).dot(flatLook);
            // Reject underfoot, behind, or nearly straight overhead attachments.
            if (forward < 0.35) {
                continue;
            }
            if (point.y < player.getY() + 1.0) {
                continue;
            }
            double horizDist = Math.sqrt(toPoint.x * toPoint.x + toPoint.z * toPoint.z);
            if (horizDist < 3.5) {
                continue;
            }

            double dist = eye.distanceTo(point);
            double elevDeg = Math.toDegrees(Math.atan2(toPoint.y, Math.max(0.1, horizDist)));
            double elevScore = 1.0 - Math.abs(elevDeg - TARGET_ELEV_DEG) / 45.0;
            double heightBonus = Mth.clamp((point.y - bodyY) / 10.0, 0.0, 1.2);
            double midRangeBonus = 1.0 - Math.abs(dist / range - 0.55);
            double score = forward * 2.4 + elevScore * 1.8 + heightBonus + midRangeBonus;
            if (score > bestScore) {
                bestScore = score;
                best = new Pivot(point, hit.getBlockPos(), false);
            }
        }
        return best;
    }

    private static Pivot virtualPivot(Player player, Vec3 eye, Vec3 look, double range,
                                      double minDist, double maxDist, double elevBias) {
        Vec3 vel = player.getDeltaMovement();
        Vec3 flatVel = new Vec3(vel.x, 0.0, vel.z);
        double horizSpeed = flatVel.length();

        Vec3 flatLook = flatNormalize(look);
        // Blend travel heading so reattach continues the line of motion.
        if (horizSpeed > 0.08) {
            Vec3 travel = flatVel.normalize();
            flatLook = normalizeSafe(flatLook.scale(0.7).add(travel.scale(0.3)));
        }

        // Target a flatter forward-up diagonal; elevBias steepens slightly but stays shallow.
        double pitch = Mth.clamp(TARGET_ELEV_DEG + elevBias * 8.0, MIN_AIM_PITCH_DEG, MAX_AIM_PITCH_DEG);
        // If the player is looking down, still shoot ahead-up so ground takeoffs work.
        if (look.y < -0.1) {
            pitch = Math.max(pitch, 28.0);
        }
        Vec3 aim = aimAtPitch(flatLook, pitch);

        double pivotDist = Mth.lerp(Mth.clamp(horizSpeed / 1.8, 0.0, 1.0), minDist, Math.min(maxDist, range));
        pivotDist = Mth.clamp(pivotDist, minDist, Math.min(maxDist, range));

        Vec3 pivot = eye.add(aim.scale(pivotDist));
        // Keep a useful forward reach; do not collapse into an overhead point.
        double minHoriz = Math.max(8.0, pivotDist * 0.7);
        Vec3 flatOff = new Vec3(pivot.x - eye.x, 0.0, pivot.z - eye.z);
        double horiz = flatOff.length();
        if (horiz < minHoriz) {
            Vec3 push = flatLook.scale(minHoriz);
            pivot = new Vec3(eye.x + push.x, pivot.y, eye.z + push.z);
        }
        double minPivotY = player.getY() + player.getBbHeight() + 1.25;
        double maxPivotY = eye.y + pivotDist * Math.sin(Math.toRadians(MAX_AIM_PITCH_DEG));
        pivot = new Vec3(pivot.x, Mth.clamp(pivot.y, minPivotY, maxPivotY), pivot.z);
        return new Pivot(pivot, BlockPos.containing(pivot), true);
    }

    private static Vec3 aimAtPitch(Vec3 flatForward, double pitchDeg) {
        double rad = Math.toRadians(pitchDeg);
        double c = Math.cos(rad);
        double s = Math.sin(rad);
        return normalizeSafe(new Vec3(flatForward.x * c, s, flatForward.z * c));
    }

    private static Vec3 flatNormalize(Vec3 v) {
        Vec3 flat = new Vec3(v.x, 0.0, v.z);
        if (flat.lengthSqr() < 1.0e-6) {
            return new Vec3(0.0, 0.0, 1.0);
        }
        return flat.normalize();
    }

    private static Vec3 normalizeSafe(Vec3 v) {
        if (v.lengthSqr() < 1.0e-8) {
            return new Vec3(0.0, 0.0, 1.0);
        }
        return v.normalize();
    }
}
