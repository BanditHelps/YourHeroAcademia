package com.github.bandithelps.cloud.events;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.cloud.ClientCloudState;
import com.github.bandithelps.client.cloud.ClientCloudVolume;
import com.github.bandithelps.cloud.CloudCellPos;
import com.github.bandithelps.cloud.CloudSimConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.Map;

@EventBusSubscriber(modid = YourHeroAcademia.MODID, value = Dist.CLIENT)
public final class CloudClientRenderEvents {
    private CloudClientRenderEvents() {
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null || minecraft.isPaused()) {
            return;
        }

        int budget = CloudSimConfig.maxParticleSpawnPerTick();
        if (budget <= 0) {
            return;
        }

        double maxDistance = CloudSimConfig.clientParticleDistance();
        double maxDistanceSqr = maxDistance * maxDistance;
        Vec3 playerPos = minecraft.player.getEyePosition();

        int spawned = 0;
        for (ClientCloudVolume volume : ClientCloudState.volumes()) {
            for (Map.Entry<CloudCellPos, Float> entry : volume.cellsView().entrySet()) {
                if (spawned >= budget) {
                    return;
                }

                Vec3 cellCenter = entry.getKey().toWorldCenter(volume.origin(), volume.cellSize());
                double distSqr = playerPos.distanceToSqr(cellCenter);
                if (distSqr > maxDistanceSqr) {
                    continue;
                }

                float density = entry.getValue();
                double distanceFactor = 1.0D - Math.min(1.0D, Math.sqrt(distSqr) / maxDistance);
                double desiredParticles = density * distanceFactor * 5.0D;
                int guaranteed = (int) Math.floor(desiredParticles);
                double fractional = desiredParticles - guaranteed;
                int attempts = guaranteed;
                if (minecraft.level.random.nextDouble() < fractional) {
                    attempts += 1;
                }

                for (int i = 0; i < attempts && spawned < budget; i++) {
                    double jitterRange = Math.max(0.2D, volume.cellSize() * 0.48D);
                    double px = cellCenter.x + ((minecraft.level.random.nextDouble() - 0.5D) * jitterRange * 2.0D);
                    double py = cellCenter.y + ((minecraft.level.random.nextDouble() - 0.5D) * jitterRange * 2.0D);
                    double pz = cellCenter.z + ((minecraft.level.random.nextDouble() - 0.5D) * jitterRange * 2.0D);

                    minecraft.level.addParticle(
                            minecraft.level.random.nextBoolean() ? ParticleTypes.SMOKE : ParticleTypes.LARGE_SMOKE,
                            px,
                            py,
                            pz,
                            (minecraft.level.random.nextDouble() - 0.5D) * 0.01D,
                            (minecraft.level.random.nextDouble() * 0.008D) + 0.001D,
                            (minecraft.level.random.nextDouble() - 0.5D) * 0.01D
                    );
                    spawned++;
                }
            }
        }
    }
}
