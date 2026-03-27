package com.github.bandithelps.client.cloud;

import com.github.bandithelps.client.particles.managed.ManagedParticleManager;
import com.github.bandithelps.cloud.CloudCellDelta;
import com.github.bandithelps.cloud.CloudCellPos;
import com.github.bandithelps.cloud.CloudMode;
import com.github.bandithelps.cloud.CloudSimConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ClientCloudVolume {
    private final UUID id;
    private final BlockPos origin;
    private final double cellSize;
    private CloudMode mode;
    private int ttl;
    private final Map<CloudCellPos, Float> cells = new HashMap<>();
    private final Map<CloudCellPos, Double> particleCarry = new HashMap<>();
    private final Map<CloudCellPos, Integer> spawnSuppressionTicks = new HashMap<>();
    private long lastVolumeDisperseTick = -1000L;

    public ClientCloudVolume(UUID id, BlockPos origin, double cellSize, CloudMode mode, int ttl) {
        this.id = id;
        this.origin = origin.immutable();
        this.cellSize = cellSize;
        this.mode = mode;
        this.ttl = ttl;
    }

    public UUID id() {
        return this.id;
    }

    public BlockPos origin() {
        return this.origin;
    }

    public double cellSize() {
        return this.cellSize;
    }

    public CloudMode mode() {
        return this.mode;
    }

    public int ttl() {
        return this.ttl;
    }

    public Map<CloudCellPos, Float> cellsView() {
        return Map.copyOf(this.cells);
    }

    public void setMode(CloudMode mode) {
        this.mode = mode;
    }

    public void setTtl(int ttl) {
        this.ttl = ttl;
    }

    public void setCells(List<CloudCellDelta> deltas) {
        this.cells.clear();
        this.particleCarry.clear();
        this.spawnSuppressionTicks.clear();
        applyDeltas(deltas);
    }

    public void applyDeltas(List<CloudCellDelta> deltas) {
        Minecraft minecraft = Minecraft.getInstance();
        Vec3 playerPos = minecraft.player != null ? minecraft.player.position() : this.origin.getCenter();
        ManagedParticleManager managedParticleManager = ManagedParticleManager.get();
        float triggerDrop = CloudSimConfig.managedDisperseTriggerDensityDrop();
        double baseImpulse = CloudSimConfig.managedDisperseImpulseStrength();
        int suppressTicks = CloudSimConfig.managedDisperseSpawnSuppressTicks();
        long gameTime = minecraft.level != null ? minecraft.level.getGameTime() : 0L;
        float totalDrop = 0.0F;
        float maxDrop = 0.0F;
        int strongDropCells = 0;
        int removedCells = 0;
        int changedCells = 0;
        Map<CloudCellPos, Double> pendingCellImpulses = new HashMap<>();

        for (CloudCellDelta delta : deltas) {
            float previousDensity = this.cells.getOrDefault(delta.pos(), 0.0F);
            float nextDensity = Math.max(0.0F, delta.density());
            float densityDrop = previousDensity - nextDensity;
            totalDrop += Math.max(0.0F, densityDrop);
            maxDrop = Math.max(maxDrop, Math.max(0.0F, densityDrop));
            if (Math.abs(nextDensity - previousDensity) > 0.0001F) {
                changedCells++;
            }
            if (densityDrop >= 0.08F) {
                strongDropCells++;
            }

            boolean shouldImpulseCell = densityDrop >= triggerDrop || (delta.density() <= 0.0F && previousDensity > 0.0F);
            if (shouldImpulseCell) {
                double severity = Mth.clamp((densityDrop * 1.6F) + (delta.density() <= 0.0F ? 0.18F : 0.0F), 0.08F, 2.2F);
                double impulseStrength = baseImpulse * severity;
                pendingCellImpulses.merge(delta.pos(), impulseStrength, Math::max);
            }

            if (delta.density() <= 0.0F) {
                this.cells.remove(delta.pos());
                this.particleCarry.remove(delta.pos());
                if (previousDensity > 0.0F) {
                    removedCells++;
                }
            } else {
                this.cells.put(delta.pos(), delta.density());
            }
        }

        float averageDrop = changedCells > 0 ? totalDrop / changedCells : 0.0F;
        boolean likelyDisperseEvent =
                removedCells >= 2
                        || maxDrop >= 0.22F
                        || (strongDropCells >= Math.max(3, changedCells / 4) && averageDrop >= 0.06F);

        if (likelyDisperseEvent) {
            boolean isOnCooldown = this.lastVolumeDisperseTick >= 0L && (gameTime - this.lastVolumeDisperseTick <= 4L);
            if (!isOnCooldown) {
                double removedRatio = changedCells > 0 ? (double) removedCells / (double) changedCells : 0.0D;
                double severity = Mth.clamp((maxDrop * 1.2D) + (averageDrop * 0.9D) + (removedRatio * 0.8D), 0.2D, 1.9D);
                double volumeStrength = baseImpulse * severity;
                managedParticleManager.applyDisperseImpulseForVolume(this.id, playerPos, volumeStrength);
                this.lastVolumeDisperseTick = gameTime;
            }
            suppressAllCellSpawns(suppressTicks);
        } else {
            for (Map.Entry<CloudCellPos, Double> pending : pendingCellImpulses.entrySet()) {
                CloudCellPos pos = pending.getKey();
                Vec3 cellCenter = pos.toWorldCenter(this.origin, this.cellSize);
                Vec3 direction = cellCenter.subtract(playerPos);
                managedParticleManager.applyDisperseImpulseForCell(this.id, pos, direction, pending.getValue());
                suppressCellSpawns(pos, suppressTicks);
            }
        }
    }

    public int consumeParticleCarry(CloudCellPos pos, double desiredParticles) {
        if (desiredParticles <= 0.0D) {
            return 0;
        }
        if (isCellSpawnSuppressed(pos)) {
            this.particleCarry.remove(pos);
            return 0;
        }

        double next = this.particleCarry.getOrDefault(pos, 0.0D) + desiredParticles;
        int attempts = Mth.floor(next);
        next -= attempts;

        if (next <= 0.0001D) {
            this.particleCarry.remove(pos);
        } else {
            this.particleCarry.put(pos, next);
        }
        return Math.max(0, attempts);
    }

    private void suppressCellSpawns(CloudCellPos pos, int ticks) {
        if (ticks <= 0) {
            return;
        }
        int existing = this.spawnSuppressionTicks.getOrDefault(pos, 0);
        this.spawnSuppressionTicks.put(pos, Math.max(existing, ticks));
        this.particleCarry.remove(pos);
    }

    private void suppressAllCellSpawns(int ticks) {
        if (ticks <= 0 || this.cells.isEmpty()) {
            return;
        }
        for (CloudCellPos pos : this.cells.keySet()) {
            suppressCellSpawns(pos, ticks);
        }
    }

    private boolean isCellSpawnSuppressed(CloudCellPos pos) {
        int ticks = this.spawnSuppressionTicks.getOrDefault(pos, 0);
        if (ticks <= 0) {
            this.spawnSuppressionTicks.remove(pos);
            return false;
        }
        ticks--;
        if (ticks <= 0) {
            this.spawnSuppressionTicks.remove(pos);
        } else {
            this.spawnSuppressionTicks.put(pos, ticks);
        }
        return true;
    }
}
