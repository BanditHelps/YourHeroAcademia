package com.github.bandithelps.cloud.events;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.cloud.CloudVolumeManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ExplosionEvent;

import java.util.List;

@EventBusSubscriber(modid = YourHeroAcademia.MODID)
public final class CloudExplosionEvents {
    private CloudExplosionEvents() {
    }

    @SubscribeEvent
    public static void onExplosionDetonate(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }

        List<BlockPos> affected = event.getAffectedBlocks();
        if (affected.isEmpty()) {
            return;
        }

        Vec3 center = estimateCenter(affected);
        double radius = estimateRadius(affected, center);
        if (radius <= 0.5D) {
            radius = 2.0D;
        }

        float strength = 1.0F;
        CloudVolumeManager.forLevel(level).disperseSphere(center, radius, strength);
    }

    private static Vec3 estimateCenter(List<BlockPos> affected) {
        double sumX = 0.0D;
        double sumY = 0.0D;
        double sumZ = 0.0D;
        for (BlockPos pos : affected) {
            sumX += pos.getX() + 0.5D;
            sumY += pos.getY() + 0.5D;
            sumZ += pos.getZ() + 0.5D;
        }
        double size = affected.size();
        return new Vec3(sumX / size, sumY / size, sumZ / size);
    }

    private static double estimateRadius(List<BlockPos> affected, Vec3 center) {
        double maxDist = 0.0D;
        for (BlockPos pos : affected) {
            double dist = center.distanceTo(new Vec3(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D));
            if (dist > maxDist) {
                maxDist = dist;
            }
        }
        return maxDist + 0.75D;
    }
}
