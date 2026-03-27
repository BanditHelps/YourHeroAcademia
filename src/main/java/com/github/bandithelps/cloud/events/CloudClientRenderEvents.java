package com.github.bandithelps.cloud.events;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.client.cloud.ClientCloudState;
import com.github.bandithelps.client.cloud.ClientCloudVolume;
import com.github.bandithelps.cloud.CloudCellPos;
import com.github.bandithelps.cloud.CloudSimConfig;
import com.github.bandithelps.particles.YhaParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.List;
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
        List<ClientCloudVolume> volumes = new ArrayList<>(ClientCloudState.volumes());
        shuffle(volumes, minecraft.level.random);
        for (ClientCloudVolume volume : volumes) {
            Map<CloudCellPos, Float> cells = volume.cellsView();
            if (cells.isEmpty()) {
                continue;
            }

            List<Map.Entry<CloudCellPos, Float>> entries = new ArrayList<>(cells.entrySet());
            shuffle(entries, minecraft.level.random);
            for (Map.Entry<CloudCellPos, Float> entry : entries) {
                if (spawned >= budget) {
                    return;
                }

                Vec3 cellCenter = entry.getKey().toWorldCenter(volume.origin(), volume.cellSize());
                double distSqr = playerPos.distanceToSqr(cellCenter);
                if (distSqr > maxDistanceSqr) {
                    continue;
                }

                float density = entry.getValue();
                float smoothedDensity = smoothDensity(cells, entry.getKey(), density);
                double distanceFactor = 1.0D - Math.min(1.0D, Math.sqrt(distSqr) / maxDistance);
                double shapedDistanceFactor = (distanceFactor * 0.75D) + 0.25D;
                // Keep smoke mostly stagnant by favoring long-lived particles and lower spawn cadence.
                double desiredParticles = (smoothedDensity * shapedDistanceFactor * 0.16D) + (density * 0.01D);
                int attempts = Math.min(volume.consumeParticleCarry(entry.getKey(), desiredParticles), 2);
                if (attempts <= 0) {
                    continue;
                }

                for (int i = 0; i < attempts && spawned < budget; i++) {
                    double jitterRange = Math.max(0.18D, volume.cellSize() * 0.45D);
                    double px = cellCenter.x + gaussianJitter(minecraft.level.random.nextGaussian(), jitterRange);
                    double py = cellCenter.y + gaussianJitter(minecraft.level.random.nextGaussian(), jitterRange);
                    double pz = cellCenter.z + gaussianJitter(minecraft.level.random.nextGaussian(), jitterRange);
                    double driftScale = 0.0015D;

                    minecraft.level.addParticle(
                            YhaParticles.STAGNANT_SMOKE.get(),
                            px,
                            py,
                            pz,
                            (minecraft.level.random.nextDouble() - 0.5D) * driftScale,
                            0.0D,
                            (minecraft.level.random.nextDouble() - 0.5D) * driftScale
                    );
                    spawned++;
                }
            }
        }
    }

    private static float smoothDensity(Map<CloudCellPos, Float> cells, CloudCellPos pos, float selfDensity) {
        float neighborSum = 0.0F;
        neighborSum += cells.getOrDefault(new CloudCellPos(pos.x() + 1, pos.y(), pos.z()), 0.0F);
        neighborSum += cells.getOrDefault(new CloudCellPos(pos.x() - 1, pos.y(), pos.z()), 0.0F);
        neighborSum += cells.getOrDefault(new CloudCellPos(pos.x(), pos.y() + 1, pos.z()), 0.0F);
        neighborSum += cells.getOrDefault(new CloudCellPos(pos.x(), pos.y() - 1, pos.z()), 0.0F);
        neighborSum += cells.getOrDefault(new CloudCellPos(pos.x(), pos.y(), pos.z() + 1), 0.0F);
        neighborSum += cells.getOrDefault(new CloudCellPos(pos.x(), pos.y(), pos.z() - 1), 0.0F);
        float neighborAverage = neighborSum / 6.0F;
        return Mth.clamp((selfDensity * 0.7F) + (neighborAverage * 0.3F), 0.0F, 1.0F);
    }

    private static double gaussianJitter(double gaussian, double range) {
        return Mth.clamp(gaussian * (range * 0.42D), -range, range);
    }

    private static <T> void shuffle(List<T> values, RandomSource random) {
        for (int i = values.size() - 1; i > 0; i--) {
            int swapIndex = random.nextInt(i + 1);
            T temp = values.get(i);
            values.set(i, values.get(swapIndex));
            values.set(swapIndex, temp);
        }
    }
}
