package com.github.bandithelps.utils.blackwhip;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared targeting helpers for Blackwhip abilities: crosshair raycasts and forward-cone sweeps.
 */
public final class BlackwhipTargeting {

    private BlackwhipTargeting() {
    }

    /** Returns the first living entity under the owner's crosshair within {@code range}, or null. */
    public static LivingEntity raycastLiving(LivingEntity owner, double range) {
        Vec3 eye = owner.getEyePosition();
        Vec3 look = owner.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB box = owner.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(
                owner, eye, end, box,
                e -> e instanceof LivingEntity && e != owner && e.isAlive() && e.isPickable(),
                range * range
        );
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
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
            if (!(e instanceof LivingEntity living) || !living.isAlive() || living == owner) {
                continue;
            }
            Vec3 toTarget = living.getBoundingBox().getCenter().subtract(eye);
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
