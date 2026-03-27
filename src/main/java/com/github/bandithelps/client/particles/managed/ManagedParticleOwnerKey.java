package com.github.bandithelps.client.particles.managed;

import com.github.bandithelps.cloud.CloudCellPos;

import java.util.UUID;

public record ManagedParticleOwnerKey(UUID volumeId, CloudCellPos cellPos) {
}
