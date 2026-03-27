package com.github.bandithelps.client.cloud;

import com.github.bandithelps.cloud.CloudCellDelta;
import com.github.bandithelps.cloud.CloudCellPos;
import com.github.bandithelps.cloud.CloudMode;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;

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
        applyDeltas(deltas);
    }

    public void applyDeltas(List<CloudCellDelta> deltas) {
        for (CloudCellDelta delta : deltas) {
            if (delta.density() <= 0.0F) {
                this.cells.remove(delta.pos());
                this.particleCarry.remove(delta.pos());
            } else {
                this.cells.put(delta.pos(), delta.density());
            }
        }
    }

    public int consumeParticleCarry(CloudCellPos pos, double desiredParticles) {
        if (desiredParticles <= 0.0D) {
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
}
