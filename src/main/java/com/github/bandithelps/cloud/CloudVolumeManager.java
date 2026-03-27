package com.github.bandithelps.cloud;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class CloudVolumeManager {
    private static final Map<ServerLevel, CloudVolumeManager> INSTANCES = new HashMap<>();

    private final ServerLevel level;
    private final Map<UUID, CloudVolume> volumes = new HashMap<>();
    private final CloudMetrics metrics = new CloudMetrics();

    private CloudVolumeManager(ServerLevel level) {
        this.level = level;
    }

    public static CloudVolumeManager forLevel(ServerLevel level) {
        return INSTANCES.computeIfAbsent(level, CloudVolumeManager::new);
    }

    public static Collection<CloudVolumeManager> allManagers() {
        return INSTANCES.values();
    }

    public static void clearLevel(ServerLevel level) {
        INSTANCES.remove(level);
    }

    public static void clearAll() {
        INSTANCES.clear();
    }

    public CloudMetrics metrics() {
        return this.metrics;
    }

    public Collection<CloudVolume> volumes() {
        return this.volumes.values();
    }

    public Optional<CloudVolume> find(UUID id) {
        return Optional.ofNullable(this.volumes.get(id));
    }

    public CloudVolume createVolume(Vec3 center, double cellSize, CloudMode mode, int ttlTicks) {
        if (this.volumes.size() >= CloudSimConfig.maxActiveVolumes()) {
            // Reclaim oldest/smallest to keep hard cap bounded.
            List<CloudVolume> sorted = new ArrayList<>(this.volumes.values());
            sorted.sort(Comparator.comparingInt(CloudVolume::cellCount));
            if (!sorted.isEmpty()) {
                this.volumes.remove(sorted.getFirst().id());
            }
        }

        CloudVolume volume = new CloudVolume(
                UUID.randomUUID(),
                this.level,
                net.minecraft.core.BlockPos.containing(center),
                cellSize,
                mode,
                ttlTicks
        );
        this.volumes.put(volume.id(), volume);
        return volume;
    }

    public void removeVolume(UUID id) {
        this.volumes.remove(id);
    }

    public void disperseSphere(Vec3 center, double radius, float strength) {
        for (CloudVolume volume : this.volumes.values()) {
            volume.disperseSphere(center, radius, strength);
        }
    }

    public void disperseDome(Vec3 center, double radius, float strength) {
        for (CloudVolume volume : this.volumes.values()) {
            volume.disperseDome(center, radius, strength);
        }
    }

    public void disperseFront(Vec3 origin, Vec3 forward, double width, double height, double depth, float strength) {
        for (CloudVolume volume : this.volumes.values()) {
            volume.disperseFront(origin, forward, width, height, depth, strength);
        }
    }

    public void disperseCone(Vec3 origin, Vec3 forward, double length, double halfAngleDegrees, float strength) {
        for (CloudVolume volume : this.volumes.values()) {
            volume.disperseCone(origin, forward, length, halfAngleDegrees, strength);
        }
    }

    public void tick() {
        this.metrics.resetFrame();
        if (this.volumes.isEmpty()) {
            return;
        }

        long started = System.nanoTime();
        List<UUID> removals = new ArrayList<>();
        for (CloudVolume volume : this.volumes.values()) {
            this.metrics.incrementVolumes();
            int changed = volume.simulateStep();
            this.metrics.addChangedCells(changed);
            this.metrics.setPendingDeltaCellCount(this.metrics.pendingDeltaCellCount() + volume.dirtyCellCount());
            if (volume.isDead()) {
                removals.add(volume.id());
            }
        }

        for (UUID id : removals) {
            this.volumes.remove(id);
        }
        this.metrics.addSimulationNanos(System.nanoTime() - started);
    }

    public ServerLevel level() {
        return this.level;
    }
}
