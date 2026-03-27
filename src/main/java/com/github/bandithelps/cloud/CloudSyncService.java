package com.github.bandithelps.cloud;

import com.github.bandithelps.network.CloudVolumeDeltaPayload;
import com.github.bandithelps.network.CloudVolumeRemovePayload;
import com.github.bandithelps.network.CloudVolumeSpawnPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class CloudSyncService {
    private static final Map<UUID, Set<UUID>> TRACKED_VOLUMES_BY_PLAYER = new ConcurrentHashMap<>();

    private CloudSyncService() {
    }

    public static void syncLevel(ServerLevel level, CloudVolumeManager manager) {
        if (level.players().isEmpty()) {
            return;
        }

        Map<UUID, CloudVolume> currentVolumes = new HashMap<>();
        Map<UUID, List<CloudCellDelta>> dirtyDeltas = new HashMap<>();
        for (CloudVolume volume : manager.volumes()) {
            currentVolumes.put(volume.id(), volume);
            List<CloudCellDelta> deltas = volume.drainDirtyDeltas();
            if (!deltas.isEmpty()) {
                dirtyDeltas.put(volume.id(), deltas);
            }
        }

        int sentPackets = 0;
        for (ServerPlayer player : level.players()) {
            Set<UUID> tracked = TRACKED_VOLUMES_BY_PLAYER.computeIfAbsent(player.getUUID(), ignored -> new HashSet<>());
            Set<UUID> visibleNow = new HashSet<>();

            for (CloudVolume volume : currentVolumes.values()) {
                if (!isInSyncRange(player, volume)) {
                    continue;
                }

                visibleNow.add(volume.id());
                if (!tracked.contains(volume.id())) {
                    PacketDistributor.sendToPlayer(player, new CloudVolumeSpawnPayload(CloudNbt.writeSpawn(volume)));
                    tracked.add(volume.id());
                    sentPackets++;
                    continue;
                }

                List<CloudCellDelta> deltas = dirtyDeltas.get(volume.id());
                if (deltas != null && !deltas.isEmpty()) {
                    PacketDistributor.sendToPlayer(player, new CloudVolumeDeltaPayload(CloudNbt.writeDelta(volume, deltas)));
                    sentPackets++;
                }
            }

            List<UUID> removals = new ArrayList<>();
            for (UUID trackedId : tracked) {
                if (!visibleNow.contains(trackedId) || !currentVolumes.containsKey(trackedId)) {
                    removals.add(trackedId);
                }
            }

            for (UUID removeId : removals) {
                PacketDistributor.sendToPlayer(player, new CloudVolumeRemovePayload(CloudNbt.writeRemove(removeId)));
                tracked.remove(removeId);
                sentPackets++;
            }
        }

        manager.metrics().addSentPackets(sentPackets);
    }

    public static void forgetPlayer(ServerPlayer player) {
        TRACKED_VOLUMES_BY_PLAYER.remove(player.getUUID());
    }

    public static void clear() {
        TRACKED_VOLUMES_BY_PLAYER.clear();
    }

    private static boolean isInSyncRange(ServerPlayer player, CloudVolume volume) {
        double maxDist = CloudSimConfig.minTrackingDistance();
        double maxDistSqr = maxDist * maxDist;
        return player.position().distanceToSqr(volume.origin().getCenter()) <= maxDistSqr;
    }
}
