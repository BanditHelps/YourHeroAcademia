package com.github.bandithelps.utils.blackwhip;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared wrist / waist attach math for chain Blackwhip (server IK + client render root lock).
 */
public final class BlackwhipChainAnchors {

    public static final int MIN_SEGMENTS = 4;
    public static final int MAX_SEGMENTS = 24;
    public static final int MIN_WRAP_JOINTS = 4;
    public static final int MAX_WRAP_JOINTS = 8;

    private BlackwhipChainAnchors() {
    }

    /** Owner wrist/hand attach. {@code partialTick} is used for interpolated pose on the client. */
    public static Vec3 resolveOwnerWrist(Entity owner, float partialTick) {
        if (!(owner instanceof LivingEntity living)) {
            return owner.getPosition(partialTick).add(0, owner.getBbHeight() * 0.5, 0);
        }

        float yaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        float pitch = Mth.lerp(partialTick, living.xRotO, living.getXRot());
        Vec3 pos = living.getPosition(partialTick);

        Vec3 fwd = Vec3.directionFromRotation(0, yaw).normalize();
        Vec3 right = fwd.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();

        float side = living.getMainArm() == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        double crouch = living.isCrouching() ? -0.18 : 0.0;
        double wristY = Math.max(0.45, Math.min(1.25, living.getBbHeight() * 0.52)) + crouch;

        // Arm forward with a little look-pitch so the hand tracks looking up/down.
        double pitchRad = Math.toRadians(Mth.clamp(pitch, -60.0f, 60.0f));
        Vec3 armDir = fwd.scale(Math.cos(pitchRad)).add(0, -Math.sin(pitchRad), 0).normalize();

        return pos
                .add(0, wristY, 0)
                .add(right.scale(0.32 * side))
                .add(armDir.scale(0.50))
                .add(right.scale(0.04 * side)); // slight outward bias off the torso
    }

    public static Vec3 resolveOwnerWrist(Entity owner) {
        return resolveOwnerWrist(owner, 1.0f);
    }

    /** Entry point onto the waist band facing the owner (contact side). */
    public static Vec3 resolveWaistEntry(LivingEntity target, Vec3 fromOwner) {
        AABB bb = target.getBoundingBox();
        double cx = (bb.minX + bb.maxX) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;
        double radius = Math.max(bb.getXsize(), bb.getZsize()) * 0.5 + 0.08;
        double waistY = bb.minY + bb.getYsize() * 0.48;
        Vec3 center = new Vec3(cx, waistY, cz);
        Vec3 flat = new Vec3(fromOwner.x - cx, 0, fromOwner.z - cz);
        if (flat.lengthSqr() < 1.0e-6) {
            float yaw = target.yBodyRot;
            flat = Vec3.directionFromRotation(0, yaw).scale(-1.0);
        }
        flat = flat.normalize();
        return center.add(flat.scale(radius));
    }

    public static int wrapJointCount(LivingEntity target) {
        AABB bb = target.getBoundingBox();
        double girth = Math.max(bb.getXsize(), bb.getZsize());
        int bySize = 4 + (int) Math.floor(girth * 2.0);
        return Mth.clamp(bySize, MIN_WRAP_JOINTS, MAX_WRAP_JOINTS);
    }

    /**
     * Multi-turn torso helix starting at the owner-facing contact angle.
     *
     * @param turns at least ~1.5 for a visible wrap
     */
    public static Vec3[] buildWaistHelix(LivingEntity target, Vec3 fromOwner, int points, float turns) {
        AABB bb = target.getBoundingBox();
        double cx = (bb.minX + bb.maxX) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;
        double radius = Math.max(bb.getXsize(), bb.getZsize()) * 0.5 + 0.08;
        double yMin = bb.minY + bb.getYsize() * 0.35;
        double yMax = bb.minY + bb.getYsize() * 0.65;

        Vec3 flat = new Vec3(fromOwner.x - cx, 0, fromOwner.z - cz);
        if (flat.lengthSqr() < 1.0e-6) {
            flat = Vec3.directionFromRotation(0, target.yBodyRot).scale(-1.0);
        }
        flat = flat.normalize();
        double baseAngle = Math.atan2(flat.z, flat.x);

        float useTurns = Math.max(1.5f, turns);
        int n = Math.max(1, points);
        Vec3[] out = new Vec3[n];
        for (int i = 0; i < n; i++) {
            double t = n == 1 ? 1.0 : i / (double) (n - 1);
            double angle = baseAngle + t * useTurns * Math.PI * 2.0;
            double y = yMin + (yMax - yMin) * t;
            out[i] = new Vec3(cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius);
        }
        return out;
    }

    public static int desiredSegmentCount(double ropeDist, float linkLength, int wrapJoints) {
        float link = Math.max(0.25f, linkLength);
        int rope = Mth.ceil(ropeDist / link) + 1;
        return Mth.clamp(rope + wrapJoints, MIN_SEGMENTS, MAX_SEGMENTS);
    }

    /** Nominal link length from config; actual per-tick link stretches between these bounds. */
    public static float minLinkLength(float nominal) {
        return Math.max(0.35f, nominal * 0.65f);
    }

    public static float maxLinkLength(float nominal) {
        return Math.max(minLinkLength(nominal) + 0.15f, nominal * 1.35f);
    }

    /**
     * Adaptive link length so the rope spans {@code ropeDist} evenly without crumpling.
     * This is what prevents bunching when the player steps closer without immediately deleting joints.
     */
    public static float adaptiveLinkLength(double ropeDist, int ropeJointCount, float nominal) {
        int links = Math.max(1, ropeJointCount - 1);
        float ideal = (float) (ropeDist / links);
        return Mth.clamp(ideal, minLinkLength(nominal), maxLinkLength(nominal));
    }

    /** Index of the longest edge in the rope portion [0 .. ropeEnd). */
    public static int longestRopeEdge(Vec3[] joints, int ropeEnd) {
        int best = 0;
        double bestLen = -1;
        for (int i = 0; i < ropeEnd - 1; i++) {
            double len = joints[i].distanceToSqr(joints[i + 1]);
            if (len > bestLen) {
                bestLen = len;
                best = i;
            }
        }
        return best;
    }

    /** Index of the shortest interior rope edge (prefer removing from mid-rope). */
    public static int shortestRemovableRopeJoint(Vec3[] joints, int ropeEnd) {
        // Removable joints are 1 .. ropeEnd-2 (keep root and rope tip).
        if (ropeEnd < 4) {
            return Math.max(1, ropeEnd - 2);
        }
        int best = 1;
        double bestLen = Double.MAX_VALUE;
        for (int i = 1; i < ropeEnd - 1; i++) {
            double len = joints[i - 1].distanceToSqr(joints[i]) + joints[i].distanceToSqr(joints[i + 1]);
            if (len < bestLen) {
                bestLen = len;
                best = i;
            }
        }
        return best;
    }

    private static final double JOINT_PROBE = 0.22;
    private static final double SURFACE_NUDGE = 0.08;

    /**
     * Moves {@code to} so the segment from {@code from} does not pass through solid blocks,
     * and pushes the point out if it ends inside a collider (ground/walls).
     */
    public static Vec3 collideSegment(Level level, Entity context, Vec3 from, Vec3 to) {
        Vec3 result = to;
        if (from.distanceToSqr(to) > 1.0e-8) {
            BlockHitResult hit = level.clip(new ClipContext(
                    from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, context));
            if (hit.getType() == HitResult.Type.BLOCK) {
                Direction face = hit.getDirection();
                result = hit.getLocation().add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(SURFACE_NUDGE));
            }
        }
        return pushOutOfBlocks(level, result);
    }

    /** If a joint sits inside solid collision, nudge it into free space (prefer up for ground). */
    public static Vec3 pushOutOfBlocks(Level level, Vec3 pos) {
        if (!isBlocked(level, pos)) {
            return pos;
        }
        // Prefer lifting out of the floor first.
        for (double dy = 0.1; dy <= 2.0; dy += 0.1) {
            Vec3 up = pos.add(0, dy, 0);
            if (!isBlocked(level, up)) {
                return up;
            }
        }
        double[][] offsets = {
                {SURFACE_NUDGE, 0, 0}, {-SURFACE_NUDGE, 0, 0},
                {0, 0, SURFACE_NUDGE}, {0, 0, -SURFACE_NUDGE},
                {0.25, 0.25, 0}, {-0.25, 0.25, 0},
                {0, 0.25, 0.25}, {0, 0.25, -0.25}
        };
        for (double[] o : offsets) {
            Vec3 candidate = pos.add(o[0], o[1], o[2]);
            if (!isBlocked(level, candidate)) {
                return candidate;
            }
        }
        return pos.add(0, 0.5, 0);
    }

    /**
     * Sequential block collision from joint 1..n-1 (joint 0 / wrist is left alone so it can stay in
     * the owner's hand volume).
     */
    public static void collideJointChain(Level level, Entity context, Vec3[] joints, int count) {
        if (count < 2) {
            return;
        }
        for (int i = 1; i < count; i++) {
            joints[i] = collideSegment(level, context, joints[i - 1], joints[i]);
        }
    }

    private static boolean isBlocked(Level level, Vec3 pos) {
        AABB box = AABB.ofSize(pos, JOINT_PROBE, JOINT_PROBE, JOINT_PROBE);
        for (VoxelShape shape : level.getBlockCollisions(null, box)) {
            if (!shape.isEmpty()) {
                return true;
            }
        }
        return false;
    }
}
