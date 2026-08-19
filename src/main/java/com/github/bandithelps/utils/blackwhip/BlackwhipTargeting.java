package com.github.bandithelps.utils.blackwhip;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared targeting helpers for Blackwhip abilities: crosshair raycasts and forward-cone sweeps.
 */
public final class BlackwhipTargeting {

    private BlackwhipTargeting() {
    }

    /**
     * Resolves a living combat target from a hit entity. Multipart bosses (Ender Dragon, etc.) expose
     * pickable {@link PartEntity} hitboxes whose parent is the actual {@link LivingEntity}.
     */
    public static LivingEntity asLivingTarget(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living.isAlive() ? living : null;
        }
        if (entity instanceof PartEntity<?> part) {
            Entity parent = part.getParent();
            if (parent instanceof LivingEntity living && living.isAlive()) {
                return living;
            }
        }
        return null;
    }

    /** Returns the first living entity under the owner's crosshair within {@code range}, or null. */
    public static LivingEntity raycastLiving(LivingEntity owner, double range) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB box = owner.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                owner, eye, end, box,
                e -> {
                    LivingEntity living = asLivingTarget(e);
                    return living != null && living != owner && e.isPickable();
                },
                range * range
        );
        return hit != null ? asLivingTarget(hit.getEntity()) : null;
    }

    /**
     * Finds living entities within a forward cone of the owner, sorted nearest-first.
     *
     * @param halfAngleDeg the cone half-angle in degrees
     * @param max          maximum number of entities to return (<= 0 for unlimited)
     */
    public static List<LivingEntity> entitiesInCone(LivingEntity owner, double range, double halfAngleDeg, int max) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle().normalize();
        double cosLimit = Math.cos(Math.toRadians(halfAngleDeg));
        AABB search = owner.getBoundingBox().inflate(range);

        List<LivingEntity> results = new ArrayList<>();
        for (Entity e : owner.level().getEntities(owner, search)) {
            LivingEntity living = asLivingTarget(e);
            if (living == null || living == owner || results.contains(living)) {
                continue;
            }
            // Prefer the struck part's box when present so huge multipart AABBs don't fake cone hits.
            AABB targetBox = e instanceof PartEntity<?> ? e.getBoundingBox() : living.getBoundingBox();
            Vec3 toTarget = targetBox.getCenter().subtract(eye);
            double dist = toTarget.length();
            if (dist > range || dist < 1.0e-4) {
                continue;
            }
            double cos = toTarget.scale(1.0 / dist).dot(look);
            if (cos >= cosLimit) {
                results.add(living);
            }
        }
        results.sort(Comparator.comparingDouble(owner::distanceToSqr));
        if (max > 0 && results.size() > max) {
            return new ArrayList<>(results.subList(0, max));
        }
        return results;
    }

    /**
     * Finds living entities in a full sphere around the owner, sorted nearest-first.
     * Walls block candidates (collider line-of-sight from the owner's eyes to the target center).
     *
     * @param max maximum number of entities to return ({@code <= 0} for unlimited)
     */
    public static List<LivingEntity> entitiesInRange(LivingEntity owner, double range, int max) {
        Vec3 eye = owner.getEyePosition();
        double rangeSqr = range * range;
        AABB search = owner.getBoundingBox().inflate(range);

        List<LivingEntity> results = new ArrayList<>();
        for (Entity e : owner.level().getEntities(owner, search)) {
            LivingEntity living = asLivingTarget(e);
            if (living == null || living == owner || !e.isPickable() || results.contains(living)) {
                continue;
            }
            if (living.distanceToSqr(owner) > rangeSqr) {
                continue;
            }
            if (!hasLineOfSight(owner, living, eye)) {
                continue;
            }
            results.add(living);
        }
        results.sort(Comparator.comparingDouble(owner::distanceToSqr));
        if (max > 0 && results.size() > max) {
            return new ArrayList<>(results.subList(0, max));
        }
        return results;
    }

    /** True when a collider clip from {@code origin} reaches {@code target} without hitting a block first. */
    public static boolean hasLineOfSight(LivingEntity owner, Entity target, Vec3 origin) {
        Vec3 to = target.position().add(0.0, target.getBbHeight() * 0.5, 0.0);
        BlockHitResult hit = owner.level().clip(new ClipContext(
                origin, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        if (hit.getType() == HitResult.Type.MISS) {
            return true;
        }
        double targetDist = origin.distanceTo(to);
        return origin.distanceTo(hit.getLocation()) >= targetDist - 0.35;
    }

    /**
     * Fans collider clips in a forward cone and returns a solid block hit within {@code range},
     * or {@code null} if nothing lands (open air / void).
     * <p>
     * Hits inside an inner forward cone win over side hits (furthest among those). If nothing
     * is ahead, remaining hits are scored by distance weighted toward look-alignment so the
     * grab stays near the center of vision instead of snapping to the left/right rim.
     *
     * @param halfAngleDeg outer-ring half-angle in degrees
     * @param rings        number of concentric sample rings (>= 1)
     * @param raysPerRing  clips per ring (>= 1)
     */
    public static BlockHitResult furthestBlockInCone(LivingEntity owner, double range, double halfAngleDeg,
                                                     int rings, int raysPerRing) {
        if (range <= 0.0 || rings < 1 || raysPerRing < 1) {
            return null;
        }
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle().normalize();
        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = worldUp.cross(look);
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1, 0, 0);
        }
        right = right.normalize();
        Vec3 up = look.cross(right).normalize();

        // Prefer anything roughly ahead of the crosshair over outer-ring side geometry.
        double innerCos = Math.cos(Math.toRadians(halfAngleDeg * 0.42));
        final double alignPower = 4.0;
        final double innerScoreBias = 1_000_000.0;

        BlockHitResult best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        int ringCount = Math.max(1, rings);
        int count = Math.max(1, raysPerRing);

        for (int ring = 1; ring <= ringCount; ring++) {
            double cone = Math.toRadians(halfAngleDeg * ring / (double) ringCount);
            double cos = Math.cos(cone);
            double sin = Math.sin(cone);
            for (int i = 0; i < count; i++) {
                double theta = (2.0 * Math.PI * i) / count + ring * 0.15;
                Vec3 offset = right.scale(Math.cos(theta)).add(up.scale(Math.sin(theta))).normalize();
                Vec3 dir = look.scale(cos).add(offset.scale(sin)).normalize();
                BlockHitResult hit = owner.level().clip(new ClipContext(
                        eye, eye.add(dir.scale(range)),
                        ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
                if (hit.getType() != HitResult.Type.BLOCK) {
                    continue;
                }
                Vec3 toHit = hit.getLocation().subtract(eye);
                double dist = toHit.length();
                if (dist < 1.0e-4) {
                    continue;
                }
                double align = toHit.scale(1.0 / dist).dot(look);
                if (align <= 0.0) {
                    continue;
                }
                boolean inner = align >= innerCos;
                double score = inner
                        ? innerScoreBias + dist
                        : dist * Math.pow(align, alignPower);
                if (score > bestScore) {
                    bestScore = score;
                    best = hit;
                }
            }
        }
        return best;
    }
}
