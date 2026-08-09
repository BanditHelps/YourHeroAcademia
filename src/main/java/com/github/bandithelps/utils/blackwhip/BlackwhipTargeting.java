package com.github.bandithelps.utils.blackwhip;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
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
}
