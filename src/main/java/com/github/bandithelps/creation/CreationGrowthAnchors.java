package com.github.bandithelps.creation;

import com.github.bandithelps.entities.CreationProductEntity;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Momo-style attach points for creation growth: back, then chest, then shoulders.
 * Uses body yaw so looking around does not swing the object off the torso.
 */
public final class CreationGrowthAnchors {
    public static final int SLOT_BACK = 0;
    public static final int SLOT_CHEST = 1;
    public static final int SLOT_LEFT_SHOULDER = 2;
    public static final int SLOT_RIGHT_SHOULDER = 3;
    public static final int SLOT_COUNT = 4;

    private static final Vec3 UP = new Vec3(0.0, 1.0, 0.0);

    private CreationGrowthAnchors() {
    }

    public static int wrapSlot(int slot) {
        return Math.floorMod(slot, SLOT_COUNT);
    }

    public static int nextSlot(Level level, Entity owner) {
        if (level == null || owner == null) {
            return SLOT_BACK;
        }
        int used = 0;
        AABB search = owner.getBoundingBox().inflate(4.0);
        for (CreationProductEntity product : level.getEntitiesOfClass(CreationProductEntity.class, search)) {
            if (product.isAlive() && product.getOwnerId() == owner.getId()) {
                used++;
            }
        }
        return used % SLOT_COUNT;
    }

    public static float bodyYaw(Entity owner, float partialTick) {
        if (owner instanceof LivingEntity living) {
            return Mth.rotLerp(partialTick, living.yBodyRotO, living.yBodyRot);
        }
        return owner.getYRot();
    }

    public static Vec3 visualPos(Entity owner, int slot, float jitterSide, float jitterUp, float partialTick) {
        if (!(owner instanceof LivingEntity living)) {
            return owner.getPosition(partialTick).add(0.0, owner.getBbHeight() * 0.55, 0.0);
        }
        Basis basis = basis(living, partialTick);
        double crouch = living.isCrouching() ? -0.18 : 0.0;
        double height = living.getBbHeight();
        int resolved = wrapSlot(slot);

        double y;
        Vec3 offset;
        switch (resolved) {
            case SLOT_CHEST -> {
                y = Mth.clamp(height * 0.55, 0.50, 1.40) + crouch;
                offset = basis.forward.scale(0.22);
            }
            case SLOT_LEFT_SHOULDER -> {
                y = Mth.clamp(height * 0.68, 0.70, 1.55) + crouch;
                offset = basis.right.scale(-0.28).add(basis.forward.scale(0.04));
            }
            case SLOT_RIGHT_SHOULDER -> {
                y = Mth.clamp(height * 0.68, 0.70, 1.55) + crouch;
                offset = basis.right.scale(0.28).add(basis.forward.scale(0.04));
            }
            default -> {
                y = Mth.clamp(height * 0.62, 0.55, 1.55) + crouch;
                offset = basis.forward.scale(-0.18);
            }
        }
        // Embed slightly so scale-up reads as pushing out of the skin.
        offset = offset
                .add(basis.right.scale(jitterSide))
                .add(0.0, jitterUp, 0.0)
                .add(normal(resolved, basis).scale(-0.04));
        return living.getPosition(partialTick).add(0.0, y, 0.0).add(offset);
    }

    public static Vec3 outwardNormal(Entity owner, int slot, float partialTick) {
        if (!(owner instanceof LivingEntity living)) {
            return new Vec3(0.0, 0.0, 1.0);
        }
        return normal(wrapSlot(slot), basis(living, partialTick));
    }

    private static Vec3 normal(int slot, Basis basis) {
        return switch (slot) {
            case SLOT_CHEST -> basis.forward;
            case SLOT_LEFT_SHOULDER -> basis.right.scale(-1.0);
            case SLOT_RIGHT_SHOULDER -> basis.right;
            default -> basis.forward.scale(-1.0);
        };
    }

    private static Basis basis(LivingEntity living, float partialTick) {
        float yaw = bodyYaw(living, partialTick);
        Vec3 forward = Vec3.directionFromRotation(0.0f, yaw).normalize();
        Vec3 right = forward.cross(UP);
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1.0, 0.0, 0.0);
        }
        return new Basis(forward, right.normalize());
    }

    private record Basis(Vec3 forward, Vec3 right) {
    }
}
