package com.github.bandithelps.client.cloud;

import com.github.bandithelps.client.particles.managed.ManagedParticleManager;
import com.github.bandithelps.cloud.CloudCellDelta;
import com.github.bandithelps.cloud.CloudMode;
import net.minecraft.core.BlockPos;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientCloudState {
    private static final Map<UUID, ClientCloudVolume> VOLUMES = new ConcurrentHashMap<>();

    private ClientCloudState() {
    }

    public static void upsertSpawn(UUID id, BlockPos origin, double cellSize, CloudMode mode, int ttl, java.util.List<CloudCellDelta> cells) {
        ClientCloudVolume volume = new ClientCloudVolume(id, origin, cellSize, mode, ttl);
        volume.setCells(cells);
        VOLUMES.put(id, volume);
    }

    public static void applyDelta(UUID id, CloudMode mode, int ttl, java.util.List<CloudCellDelta> deltas) {
        ClientCloudVolume volume = VOLUMES.get(id);
        if (volume == null) {
            return;
        }
        volume.setMode(mode);
        volume.setTtl(ttl);
        volume.applyDeltas(deltas);
        if (volume.cellsView().isEmpty()) {
            VOLUMES.remove(id);
            ManagedParticleManager.get().despawnVolume(id);
        }
    }

    public static void remove(UUID id) {
        VOLUMES.remove(id);
        ManagedParticleManager.get().despawnVolume(id);
    }

    public static void clear() {
        VOLUMES.clear();
        ManagedParticleManager.get().clear();
    }

    public static Collection<ClientCloudVolume> volumes() {
        return VOLUMES.values();
    }
}
