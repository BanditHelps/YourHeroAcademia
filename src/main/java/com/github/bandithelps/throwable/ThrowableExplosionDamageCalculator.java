package com.github.bandithelps.throwable;

import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.SimpleExplosionDamageCalculator;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Lets throwables set entity damage independently of blast radius.
 * {@code maxDamage < 0} keeps vanilla's radius-scaled formula.
 *
 * Occlusion only counts full collision cubes. Grass, flowers, slabs, and other
 * non-solid blocks do not soak the blast. The block the grenade is sitting on
 * is ignored when a ray only grazes its top face.
 */
public final class ThrowableExplosionDamageCalculator extends SimpleExplosionDamageCalculator {
    private final float maxDamage;

    public ThrowableExplosionDamageCalculator(boolean explodesBlocks, boolean damagesEntities, float knockback, float maxDamage) {
        super(explodesBlocks, damagesEntities, Optional.of(knockback), Optional.empty());
        this.maxDamage = maxDamage;
    }

    @Override
    public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
        if (this.maxDamage < 0.0f) {
            return super.getEntityDamageAmount(explosion, entity, exposure);
        }
        if (this.maxDamage == 0.0f) {
            return 0.0f;
        }

        float radius = explosion.radius();
        if (radius <= 0.0f) {
            return 0.0f;
        }

        Vec3 center = explosion.center();
        float closest = closestDistance(center, entity.getBoundingBox());
        float proximity = 1.0f - Mth.clamp(closest / radius, 0.0f, 1.0f);
        if (proximity <= 0.0f) {
            return 0.0f;
        }

        float seen = seenPercent(center, entity);
        return proximity * seen * this.maxDamage;
    }

    private static float closestDistance(Vec3 point, AABB box) {
        double x = Mth.clamp(point.x, box.minX, box.maxX);
        double y = Mth.clamp(point.y, box.minY, box.maxY);
        double z = Mth.clamp(point.z, box.minZ, box.maxZ);
        return (float) point.distanceTo(new Vec3(x, y, z));
    }

    /**
     * Same sample grid as vanilla {@code ServerExplosion.getSeenPercent}, but rays
     * pass through non-full blocks and the top of the blast's support block.
     */
    private static float seenPercent(Vec3 center, Entity entity) {
        AABB bb = entity.getBoundingBox();
        double xs = 1.0 / ((bb.maxX - bb.minX) * 2.0 + 1.0);
        double ys = 1.0 / ((bb.maxY - bb.minY) * 2.0 + 1.0);
        double zs = 1.0 / ((bb.maxZ - bb.minZ) * 2.0 + 1.0);
        if (xs < 0.0 || ys < 0.0 || zs < 0.0) {
            return 0.0f;
        }

        double xOffset = (1.0 - Math.floor(1.0 / xs) * xs) / 2.0;
        double zOffset = (1.0 - Math.floor(1.0 / zs) * zs) / 2.0;
        BlockPos blastCell = BlockPos.containing(center);
        BlockPos support = BlockPos.containing(center.x, center.y - 0.5, center.z);
        Level level = entity.level();
        int hits = 0;
        int count = 0;

        for (double xx = 0.0; xx <= 1.0; xx += xs) {
            for (double yy = 0.0; yy <= 1.0; yy += ys) {
                for (double zz = 0.0; zz <= 1.0; zz += zs) {
                    Vec3 from = new Vec3(
                            Mth.lerp(xx, bb.minX, bb.maxX) + xOffset,
                            Mth.lerp(yy, bb.minY, bb.maxY),
                            Mth.lerp(zz, bb.minZ, bb.maxZ) + zOffset
                    );
                    if (rayReaches(level, from, center, entity, blastCell, support)) {
                        hits++;
                    }
                    count++;
                }
            }
        }
        return count == 0 ? 0.0f : (float) hits / (float) count;
    }

    private static boolean rayReaches(Level level, Vec3 from, Vec3 to, Entity entity, BlockPos blastCell, BlockPos support) {
        Vec3 delta = to.subtract(from);
        double length = delta.length();
        if (length < 1.0E-4) {
            return true;
        }
        Vec3 unit = delta.scale(1.0 / length);
        Vec3 current = from;
        double advanced = 0.0;
        for (int i = 0; i < 24; i++) {
            BlockHitResult hit = level.clip(new ClipContext(current, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, entity));
            if (hit.getType() != HitResult.Type.BLOCK) {
                return true;
            }
            BlockPos pos = hit.getBlockPos();
            boolean grazedSupport = pos.equals(support) && hit.getDirection() == Direction.UP;
            if (!grazedSupport && !pos.equals(blastCell) && isOccluder(level.getBlockState(pos), level, pos)) {
                return false;
            }
            advanced = from.distanceTo(hit.getLocation()) + 0.08;
            if (advanced >= length) {
                return true;
            }
            current = from.add(unit.scale(advanced));
        }
        return true;
    }

    private static boolean isOccluder(BlockState state, BlockGetter level, BlockPos pos) {
        if (state.isAir() || state.canBeReplaced()) {
            return false;
        }
        var shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty()) {
            return false;
        }
        return Block.isShapeFullBlock(shape);
    }
}
