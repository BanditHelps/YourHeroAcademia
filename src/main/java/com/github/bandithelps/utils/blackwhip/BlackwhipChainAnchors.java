package com.github.bandithelps.utils.blackwhip;

import net.minecraft.core.BlockPos;
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

import java.util.List;

/**
 * Shared wrist / wrap-band attach math for chain Blackwhip (server IK + client render root lock).
 */
public final class BlackwhipChainAnchors {

    public static final int MIN_SEGMENTS = 4;
    public static final int MAX_SEGMENTS = 24;
    /** Tip hit-proxy segments parked at the wrap entry (visual coil is client-authored). */
    public static final int MIN_WRAP_JOINTS = 1;
    public static final int MAX_WRAP_JOINTS = 2;
    /** Dense samples for the client-side wrap coil ribbon. */
    public static final int RENDER_COIL_SAMPLES = 36;
    /** Extra radius beyond the AABB so wrap rings sit outside the body. */
    public static final double WRAP_RADIUS_PAD = 0.22;
    /** Half the vertical gap between the two wrap rings (fraction of hitbox height). */
    public static final double WRAP_RING_HALF_SPACING = 0.14;
    /** Fallback wrap band when no latch hit is available. */
    public static final float DEFAULT_WRAP_HEIGHT = 0.50f;
    /** Lowest / highest allowed wrap-band centers (fraction of hitbox height). */
    public static final float MIN_WRAP_HEIGHT = 0.22f;
    public static final float MAX_WRAP_HEIGHT = 0.88f;
    /**
     * Extra drop below eyeline (as a fraction of hitbox height) for first-person victim wrap
     * rendering so the coil sits under the camera instead of filling the lens.
     */
    public static final float FIRST_PERSON_WRAP_BELOW_EYE = 0.12f;

    private BlackwhipChainAnchors() {
    }

    /**
     * Functional tip spawn for chain deploy: eye height along look, slightly forward so the tip
     * rides the crosshair ray. Visual rope root remains {@link #resolveOwnerWrist}.
     */
    public static Vec3 resolveTipSpawn(Entity owner, Vec3 look) {
        Vec3 dir = look.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : look.normalize();
        Vec3 eye = owner instanceof LivingEntity living
                ? living.getEyePosition()
                : owner.position().add(0.0, owner.getBbHeight() * 0.85, 0.0);
        return eye.add(dir.scale(0.25));
    }

    /** Owner wrist/hand attach on the owner's main arm. */
    public static Vec3 resolveOwnerWrist(Entity owner, float partialTick) {
        HumanoidArm arm = owner instanceof LivingEntity living ? living.getMainArm() : HumanoidArm.RIGHT;
        return resolveOwnerWrist(owner, partialTick, arm);
    }

    /** Owner wrist/hand attach on a specific arm. {@code partialTick} interpolates client pose. */
    public static Vec3 resolveOwnerWrist(Entity owner, float partialTick, HumanoidArm arm) {
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

        HumanoidArm useArm = arm != null ? arm : living.getMainArm();
        float side = useArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        double crouch = living.isCrouching() ? -0.20 : 0.0;
        // Lower forearm / wrist band — sits on the hand rather than floating ahead of it.
        double wristY = Math.max(0.42, Math.min(1.20, living.getBbHeight() * 0.49)) + crouch;

        // Mild look-pitch so the hand tracks looking up/down without becoming a wand.
        double pitchRad = Math.toRadians(Mth.clamp(pitch, -45.0f, 45.0f) * 0.55f);
        Vec3 armDir = fwd.scale(Math.cos(pitchRad)).add(0, -Math.sin(pitchRad), 0).normalize();

        return pos
                .add(0, wristY, 0)
                .add(right.scale(0.29 * side))
                .add(armDir.scale(0.24));
    }

    /**
     * First-person local visual attach (client only). Places the whip root near the held hand
     * relative to the camera so it reads as emerging from the arm like in the anime.
     */
    public static Vec3 resolveFirstPersonHand(LivingEntity owner, float partialTick) {
        return resolveFirstPersonHand(owner, partialTick, owner.getMainArm());
    }

    public static Vec3 resolveFirstPersonHand(LivingEntity owner, float partialTick, HumanoidArm arm) {
        Vec3 eye = owner.getEyePosition(partialTick);
        float yaw = Mth.lerp(partialTick, owner.yRotO, owner.getYRot());
        float pitch = Mth.lerp(partialTick, owner.xRotO, owner.getXRot());
        Vec3 look = Vec3.directionFromRotation(pitch, yaw).normalize();
        Vec3 right = look.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0e-6) {
            right = Vec3.directionFromRotation(0, yaw).cross(new Vec3(0, 1, 0));
        }
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = right.cross(look).normalize();

        HumanoidArm useArm = arm != null ? arm : owner.getMainArm();
        float side = useArm == HumanoidArm.RIGHT ? 1.0f : -1.0f;
        // Left/offhand sits lower and wider in vanilla first-person than the main hand.
        double forward = useArm == HumanoidArm.LEFT ? 0.36 : 0.42;
        double lateral = useArm == HumanoidArm.LEFT ? 0.38 : 0.28;
        double down = useArm == HumanoidArm.LEFT ? 0.42 : 0.22;
        return eye
                .add(look.scale(forward))
                .add(right.scale(lateral * side))
                .add(up.scale(-down));
    }

    public static Vec3 resolveOwnerWrist(Entity owner) {
        return resolveOwnerWrist(owner, 1.0f);
    }

    /**
     * Upper-back attach (between the shoulder blades), using body yaw so looking around does not
     * swing the root. Used by chain Block Toss.
     */
    public static Vec3 resolveOwnerBack(Entity owner, float partialTick) {
        if (!(owner instanceof LivingEntity living)) {
            return owner.getPosition(partialTick).add(0.0, owner.getBbHeight() * 0.55, 0.0);
        }
        float yaw = Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        Vec3 pos = living.getPosition(partialTick);
        Vec3 back = Vec3.directionFromRotation(0.0f, yaw).normalize().scale(-1.0);
        double crouch = living.isCrouching() ? -0.18 : 0.0;
        double backY = Math.max(0.55, Math.min(1.55, living.getBbHeight() * 0.62)) + crouch;
        return pos.add(0.0, backY, 0.0).add(back.scale(0.32));
    }

    public static Vec3 resolveOwnerBack(Entity owner) {
        return resolveOwnerBack(owner, 1.0f);
    }

    public static Vec3 resolveOwnerAttach(Entity owner, float partialTick, boolean fromBack) {
        return resolveOwnerAttach(owner, partialTick, fromBack, null);
    }

    public static Vec3 resolveOwnerAttach(Entity owner, float partialTick, boolean fromBack, HumanoidArm arm) {
        return fromBack ? resolveOwnerBack(owner, partialTick) : resolveOwnerWrist(owner, partialTick, arm);
    }

    public static Vec3 resolveOwnerAttach(Entity owner, boolean fromBack) {
        return resolveOwnerAttach(owner, 1.0f, fromBack);
    }

    public static Vec3 resolveOwnerAttach(Entity owner, boolean fromBack, HumanoidArm arm) {
        return resolveOwnerAttach(owner, 1.0f, fromBack, arm);
    }

    /**
     * Client render AABB from interpolated entity pose so wrap/tip track the drawn model,
     * not the tick-old box.
     */
    public static AABB interpolatedAabb(LivingEntity target, float partialTick) {
        Vec3 pos = target.getPosition(partialTick);
        double halfW = target.getBbWidth() * 0.5;
        double h = target.getBbHeight();
        return new AABB(pos.x - halfW, pos.y, pos.z - halfW, pos.x + halfW, pos.y + h, pos.z + halfW);
    }

    /** Axis-aligned cube of {@code size} centered on {@code center}. */
    public static AABB cubeAround(Vec3 center, double size) {
        double h = size * 0.5;
        return new AABB(center.x - h, center.y - h, center.z - h, center.x + h, center.y + h, center.z + h);
    }

    public static AABB blockAabb(BlockPos pos) {
        return new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.0, pos.getZ() + 1.0);
    }

    /**
     * Client-only wrap height for a grabbed player viewing themselves in first person: band
     * center sits just below the eyeline so the upper coil ring stays under the camera.
     */
    public static float firstPersonTargetWrapHeight(LivingEntity target) {
        if (target == null || target.getBbHeight() < 1.0e-6) {
            return DEFAULT_WRAP_HEIGHT;
        }
        float eyeFrac = Mth.clamp((float) (target.getEyeHeight() / target.getBbHeight()), 0.5f, 1.0f);
        // Keep the upper ring under the camera: mid + half-spacing + pad ≤ eye.
        float mid = eyeFrac - (float) WRAP_RING_HALF_SPACING - FIRST_PERSON_WRAP_BELOW_EYE;
        return Mth.clamp(mid, MIN_WRAP_HEIGHT, 0.70f);
    }

    /**
     * Converts a world-space tip hit into a wrap-band height fraction of the target AABB.
     * Crown hits bias slightly downward onto the upper torso (where most of the hitbox girth
     * lives); ankle hits bias upward onto the lower torso. Mid-body hits stay near the tip.
     */
    public static float computeWrapHeight(LivingEntity target, Vec3 hitPos) {
        if (target == null || hitPos == null) {
            return DEFAULT_WRAP_HEIGHT;
        }
        return computeWrapHeight(target.getBoundingBox(), hitPos);
    }

    public static float computeWrapHeight(AABB bb, Vec3 hitPos) {
        double h = bb.getYsize();
        if (h < 1.0e-6) {
            return DEFAULT_WRAP_HEIGHT;
        }
        float raw = Mth.clamp((float) ((hitPos.y - bb.minY) / h), 0.0f, 1.0f);
        float biased = raw;
        // Above ~shoulders: slide down toward upper chest as we near the crown.
        if (raw > 0.72f) {
            float t = (raw - 0.72f) / (1.0f - 0.72f);
            biased = Mth.lerp(t * t, raw, 0.62f);
        } else if (raw < 0.28f) {
            // Below hips: slide up toward lower torso so rings don't hug the feet.
            float t = (0.28f - raw) / 0.28f;
            biased = Mth.lerp(t * t, raw, 0.38f);
        }
        return Mth.clamp(biased, MIN_WRAP_HEIGHT, MAX_WRAP_HEIGHT);
    }

    /** Entry point onto the wrap band facing the owner (contact side). */
    public static Vec3 resolveWaistEntry(LivingEntity target, Vec3 fromOwner) {
        return resolveWaistEntry(target.getBoundingBox(), fromOwner, target.yBodyRot, DEFAULT_WRAP_HEIGHT);
    }

    public static Vec3 resolveWaistEntry(LivingEntity target, Vec3 fromOwner, float wrapHeight) {
        return resolveWaistEntry(target.getBoundingBox(), fromOwner, target.yBodyRot, wrapHeight);
    }

    /** Interpolated client entry at the latch wrap height. */
    public static Vec3 resolveWaistEntry(LivingEntity target, Vec3 fromOwner, float partialTick,
                                         float wrapHeight) {
        float yaw = Mth.rotLerp(partialTick, target.yBodyRotO, target.yBodyRot);
        return resolveWaistEntry(interpolatedAabb(target, partialTick), fromOwner, yaw, wrapHeight);
    }

    public static Vec3 resolveWaistEntry(AABB bb, Vec3 fromOwner, float bodyYaw, float wrapHeight) {
        double cx = (bb.minX + bb.maxX) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;
        double radius = Math.max(bb.getXsize(), bb.getZsize()) * 0.5 + WRAP_RADIUS_PAD;
        float mid = Mth.clamp(wrapHeight, MIN_WRAP_HEIGHT, MAX_WRAP_HEIGHT);
        // Meet the lower wrap ring (band center minus spacing).
        double waistY = bb.minY + bb.getYsize() * (mid - WRAP_RING_HALF_SPACING);
        Vec3 center = new Vec3(cx, waistY, cz);
        Vec3 flat = new Vec3(fromOwner.x - cx, 0, fromOwner.z - cz);
        if (flat.lengthSqr() < 1.0e-6) {
            flat = Vec3.directionFromRotation(0, bodyYaw).scale(-1.0);
        }
        flat = flat.normalize();
        return center.add(flat.scale(radius));
    }

    /** Tip hit-proxy count only (1–2); the visible coil is built on the client. */
    public static int wrapJointCount(LivingEntity target) {
        AABB bb = target.getBoundingBox();
        double girth = Math.max(bb.getXsize(), bb.getZsize());
        return girth > 1.2 ? MAX_WRAP_JOINTS : MIN_WRAP_JOINTS;
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

    /**
     * Dense nearly-flat coil for client ribbon rendering. Starts at the wrap entry so it joins
     * the rope tip cleanly, then loops around the body at the latch height band.
     */
    public static Vec3[] buildRenderCoil(LivingEntity target, Vec3 fromOwner, int samples, float turns) {
        return buildRenderCoil(target, fromOwner, samples, turns, 1.0f, DEFAULT_WRAP_HEIGHT);
    }

    public static Vec3[] buildRenderCoil(LivingEntity target, Vec3 fromOwner, int samples, float turns,
                                         float partialTick) {
        return buildRenderCoil(target, fromOwner, samples, turns, partialTick, DEFAULT_WRAP_HEIGHT);
    }

    public static Vec3[] buildRenderCoil(LivingEntity target, Vec3 fromOwner, int samples, float turns,
                                         float partialTick, float wrapHeight) {
        AABB bb = interpolatedAabb(target, partialTick);
        float yaw = Mth.rotLerp(partialTick, target.yBodyRotO, target.yBodyRot);
        double cx = (bb.minX + bb.maxX) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;
        double radius = Math.max(bb.getXsize(), bb.getZsize()) * 0.5 + WRAP_RADIUS_PAD;
        float mid = Mth.clamp(wrapHeight, MIN_WRAP_HEIGHT, MAX_WRAP_HEIGHT);
        Vec3 entry = resolveWaistEntry(bb, fromOwner, yaw, mid);
        // Two rings spaced around the latch height (lower → upper over the coil turns).
        double halfGap = bb.getYsize() * WRAP_RING_HALF_SPACING;
        double midY = bb.minY + bb.getYsize() * mid;
        double yLow = Math.max(bb.minY + bb.getYsize() * 0.05, midY - halfGap);
        double yHigh = Math.min(bb.maxY - bb.getYsize() * 0.05, midY + halfGap);
        if (yHigh < yLow) {
            yHigh = yLow;
        }

        Vec3 flat = new Vec3(fromOwner.x - cx, 0, fromOwner.z - cz);
        if (flat.lengthSqr() < 1.0e-6) {
            flat = Vec3.directionFromRotation(0, yaw).scale(-1.0);
        }
        flat = flat.normalize();
        double baseAngle = Math.atan2(flat.z, flat.x);

        // Prefer two full loops so the spaced bands read as distinct rings.
        float useTurns = Math.max(2.0f, turns);
        int n = Math.max(2, samples);
        Vec3[] out = new Vec3[n];
        out[0] = entry;
        for (int i = 1; i < n; i++) {
            double t = i / (double) (n - 1);
            double angle = baseAngle + t * useTurns * Math.PI * 2.0;
            double y = yLow + (yHigh - yLow) * t;
            out[i] = new Vec3(cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius);
        }
        return out;
    }

    public static Vec3[] buildRenderCoil(AABB bb, Vec3 fromOwner, int samples, float turns, float wrapHeight) {
        double cx = (bb.minX + bb.maxX) * 0.5;
        double cz = (bb.minZ + bb.maxZ) * 0.5;
        double radius = Math.max(bb.getXsize(), bb.getZsize()) * 0.5 + WRAP_RADIUS_PAD;
        float mid = Mth.clamp(wrapHeight, MIN_WRAP_HEIGHT, MAX_WRAP_HEIGHT);
        Vec3 entry = resolveWaistEntry(bb, fromOwner, 0.0f, mid);
        double halfGap = bb.getYsize() * WRAP_RING_HALF_SPACING;
        double midY = bb.minY + bb.getYsize() * mid;
        double yLow = Math.max(bb.minY + bb.getYsize() * 0.05, midY - halfGap);
        double yHigh = Math.min(bb.maxY - bb.getYsize() * 0.05, midY + halfGap);
        if (yHigh < yLow) {
            yHigh = yLow;
        }

        Vec3 flat = new Vec3(fromOwner.x - cx, 0, fromOwner.z - cz);
        if (flat.lengthSqr() < 1.0e-6) {
            flat = new Vec3(1.0, 0.0, 0.0);
        }
        flat = flat.normalize();
        double baseAngle = Math.atan2(flat.z, flat.x);

        float useTurns = Math.max(2.0f, turns);
        int n = Math.max(2, samples);
        Vec3[] out = new Vec3[n];
        out[0] = entry;
        for (int i = 1; i < n; i++) {
            double t = i / (double) (n - 1);
            double angle = baseAngle + t * useTurns * Math.PI * 2.0;
            double y = yLow + (yHigh - yLow) * t;
            out[i] = new Vec3(cx + Math.cos(angle) * radius, y, cz + Math.sin(angle) * radius);
        }
        return out;
    }

    /**
     * Rescales a polyline so joints[0] stays at {@code root}, joints[n-1] lands on {@code tip},
     * and intermediate points keep their relative arc-length proportions / sag.
     */
    public static void redistributeJoints(List<Vec3> joints, Vec3 root, Vec3 tip) {
        int n = joints.size();
        if (n < 2) {
            return;
        }
        if (n == 2) {
            joints.set(0, root);
            joints.set(1, tip);
            return;
        }

        Vec3 oldRoot = joints.get(0);
        Vec3 oldTip = joints.get(n - 1);

        double[] cum = new double[n];
        cum[0] = 0.0;
        for (int i = 1; i < n; i++) {
            cum[i] = cum[i - 1] + Math.max(1.0e-6, joints.get(i - 1).distanceTo(joints.get(i)));
        }
        double total = cum[n - 1];
        if (total < 1.0e-6) {
            for (int i = 0; i < n; i++) {
                double t = i / (double) (n - 1);
                joints.set(i, root.lerp(tip, t));
            }
            return;
        }

        Vec3 oldChord = oldTip.subtract(oldRoot);
        Vec3 newChord = tip.subtract(root);
        double oldLen = oldChord.length();
        double newLen = newChord.length();
        Vec3 oldDir = oldLen > 1.0e-6 ? oldChord.scale(1.0 / oldLen) : new Vec3(0, 1, 0);
        Vec3 newDir = newLen > 1.0e-6 ? newChord.scale(1.0 / newLen) : oldDir;
        double scale = oldLen > 1.0e-6 ? newLen / oldLen : 1.0;
        double oldAng = Math.atan2(oldDir.z, oldDir.x);
        double newAng = Math.atan2(newDir.z, newDir.x);
        double dAng = newAng - oldAng;
        double cos = Math.cos(dAng);
        double sin = Math.sin(dAng);

        for (int i = 1; i < n - 1; i++) {
            double t = cum[i] / total;
            Vec3 p = joints.get(i);
            Vec3 onOld = oldRoot.lerp(oldTip, t);
            Vec3 offset = p.subtract(onOld);
            double rx = (offset.x * cos - offset.z * sin) * scale;
            double rz = (offset.x * sin + offset.z * cos) * scale;
            Vec3 onNew = root.lerp(tip, t);
            joints.set(i, onNew.add(rx, offset.y * scale, rz));
        }
        joints.set(0, root);
        joints.set(n - 1, tip);
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
    /** Offset along the hit face normal so block latches sit outside the collider. */
    private static final double ATTACH_NUDGE = 0.12;

    /**
     * World attach point for a block ray hit: exact impact, nudged along the hit face normal
     * so the tip sits on that face (not flushed into the block / pushed to the top).
     */
    public static Vec3 surfaceAttachPoint(BlockHitResult hit) {
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
            return hit != null ? hit.getLocation() : Vec3.ZERO;
        }
        Direction face = hit.getDirection();
        return hit.getLocation().add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(ATTACH_NUDGE));
    }

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
                // Prefer escaping along the hit face so wall/ceiling contacts don't lift to the top.
                return pushOutOfBlocks(level, result, face);
            }
        }
        return pushOutOfBlocks(level, result);
    }

    /** If a joint sits inside solid collision, nudge it into free space (prefer up for ground). */
    public static Vec3 pushOutOfBlocks(Level level, Vec3 pos) {
        return pushOutOfBlocks(level, pos, Direction.UP);
    }

    /**
     * If a joint sits inside solid collision, nudge it into free space.
     * When {@code prefer} is set (e.g. the hit face), escape along that axis first so side/ceiling
     * contacts do not default to the top of the block.
     */
    public static Vec3 pushOutOfBlocks(Level level, Vec3 pos, Direction prefer) {
        if (!isBlocked(level, pos)) {
            return pos;
        }
        Direction axis = prefer != null ? prefer : Direction.UP;
        Vec3 step = Vec3.atLowerCornerOf(axis.getUnitVec3i());
        for (double d = 0.1; d <= 2.0; d += 0.1) {
            Vec3 along = pos.add(step.scale(d));
            if (!isBlocked(level, along)) {
                return along;
            }
        }
        // Fallback: try remaining cardinals (UP next if we didn't already).
        Direction[] fallback = {
                Direction.UP, Direction.DOWN,
                Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST
        };
        for (Direction dir : fallback) {
            if (dir == axis) {
                continue;
            }
            Vec3 unit = Vec3.atLowerCornerOf(dir.getUnitVec3i());
            for (double d = 0.1; d <= 1.0; d += 0.1) {
                Vec3 candidate = pos.add(unit.scale(d));
                if (!isBlocked(level, candidate)) {
                    return candidate;
                }
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
        return pos.add(step.scale(0.5));
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
