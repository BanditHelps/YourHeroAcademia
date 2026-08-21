package com.github.bandithelps.utils;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CommonUtils {

    /**
     * Given a player, grabs the entity that they are looking at in a range
     * @param player
     * @return
     */
    public static LivingEntity getTargetedLivingEntity(Player player, float range) {
        HitResult hit = player.pick(range, 0.0f, false);
        if (hit == null) return null;

        // Cast out and grab the entity that is in front of the player that they hit
        // Determines the closest
        Vec3 start = player.getEyePosition(1.0f);
        Vec3 look = player.getLookAngle();
        Vec3 end = start.add(look.scale(range));
        AABB aabb = new AABB(start, end).inflate(1.0);
        List<LivingEntity> entities = player.level().getEntitiesOfClass(LivingEntity.class, aabb, e -> e != player && e.isPickable());
        LivingEntity closest = null;
        double closestDist = Double.MAX_VALUE;
        for (LivingEntity e : entities) {
            double dist = e.distanceToSqr(player);
            if (dist < closestDist) {
                closest = e;
                closestDist = dist;
            }
        }
        return closest;
    }

}
