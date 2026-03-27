package com.github.bandithelps.cloud;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class CloudVolume {
    private static final float DENSITY_EPSILON = 0.01F;
    private static final List<CloudCellPos> DIFFUSION_NEIGHBORS = List.of(
            new CloudCellPos(1, 0, 0),
            new CloudCellPos(-1, 0, 0),
            new CloudCellPos(0, 1, 0),
            new CloudCellPos(0, -1, 0),
            new CloudCellPos(0, 0, 1),
            new CloudCellPos(0, 0, -1)
    );

    private final UUID id;
    private final ServerLevel level;
    private final BlockPos origin;
    private final double cellSize;
    private CloudMode mode;
    private int ttlTicks;
    private int initialTtlTicks;
    private final Map<CloudCellPos, Float> densityByCell = new HashMap<>();
    private final Set<CloudCellPos> dirtyCells = new HashSet<>();
    private final ArrayDeque<CloudCellPos> floodQueue = new ArrayDeque<>();
    private final Set<CloudCellPos> floodQueued = new HashSet<>();

    public CloudVolume(UUID id, ServerLevel level, BlockPos origin, double cellSize, CloudMode mode, int ttlTicks) {
        this.id = id;
        this.level = level;
        this.origin = origin.immutable();
        this.cellSize = Math.max(0.25D, cellSize);
        this.mode = mode;
        this.ttlTicks = Math.max(1, ttlTicks);
        this.initialTtlTicks = this.ttlTicks;
    }

    public UUID id() {
        return this.id;
    }

    public BlockPos origin() {
        return this.origin;
    }

    public ServerLevel level() {
        return this.level;
    }

    public double cellSize() {
        return this.cellSize;
    }

    public CloudMode mode() {
        return this.mode;
    }

    public int ttlTicks() {
        return this.ttlTicks;
    }

    public int cellCount() {
        return this.densityByCell.size();
    }

    public boolean isDead() {
        return this.ttlTicks <= 0 || this.densityByCell.isEmpty();
    }

    public Map<CloudCellPos, Float> allCellsView() {
        return Map.copyOf(this.densityByCell);
    }

    public void setMode(CloudMode mode) {
        this.mode = mode;
    }

    public void setTtlTicks(int ttlTicks) {
        int clamped = Math.max(1, ttlTicks);
        this.ttlTicks = clamped;
        this.initialTtlTicks = Math.max(this.initialTtlTicks, clamped);
    }

    public void queueFloodSeed(Vec3 worldPos) {
        CloudCellPos cell = CloudCellPos.fromWorld(worldPos, this.cellSize, this.origin);
        if (!canOccupy(cell)) {
            return;
        }
        if (this.floodQueued.add(cell)) {
            this.floodQueue.add(cell);
        }
    }

    public void addSphereDensity(Vec3 center, double radius, float densityAmount) {
        applyRadial(center, radius, (currentDensity, falloff) -> currentDensity + (densityAmount * falloff));
        setTtlTicks(Math.max(this.ttlTicks, CloudSimConfig.defaultLifetimeTicks()));
    }

    public void disperseSphere(Vec3 center, double radius, float strength) {
        applyRadial(center, radius, (currentDensity, falloff) -> currentDensity - (strength * falloff));
    }

    public void disperseDome(Vec3 center, double radius, float strength) {
        int radiusCells = Math.max(1, (int) Math.ceil(radius / this.cellSize));
        CloudCellPos centerCell = CloudCellPos.fromWorld(center, this.cellSize, this.origin);

        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dy = -radiusCells; dy <= radiusCells; dy++) {
                for (int dz = -radiusCells; dz <= radiusCells; dz++) {
                    double dist = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)) * this.cellSize;
                    if (dist > radius) {
                        continue;
                    }

                    CloudCellPos pos = new CloudCellPos(centerCell.x() + dx, centerCell.y() + dy, centerCell.z() + dz);
                    float existing = this.densityByCell.getOrDefault(pos, 0.0F);
                    if (existing <= 0.0F) {
                        continue;
                    }

                    float falloff = CloudDispersalMath.radialFalloff(dist, radius);
                    setDensity(pos, existing - (strength * falloff));
                }
            }
        }
    }

    public void disperseFront(Vec3 origin, Vec3 forward, double width, double height, double depth, float strength) {
        if (depth <= 0.0D || width <= 0.0D || height <= 0.0D) {
            return;
        }

        Vec3 normalizedForward = normalizeOrDefault(forward, new Vec3(0.0D, 0.0D, 1.0D));
        Vec3 worldUp = new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 right = normalizedForward.cross(worldUp);
        if (right.lengthSqr() < 0.000001D) {
            right = new Vec3(1.0D, 0.0D, 0.0D);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(normalizedForward).normalize();

        double halfWidth = width * 0.5D;
        double halfHeight = height * 0.5D;
        List<CloudCellPos> cells = List.copyOf(this.densityByCell.keySet());
        for (CloudCellPos pos : cells) {
            float existing = this.densityByCell.getOrDefault(pos, 0.0F);
            if (existing <= 0.0F) {
                continue;
            }
            Vec3 cellCenter = pos.toWorldCenter(this.origin, this.cellSize);
            Vec3 toCell = cellCenter.subtract(origin);
            double forwardDist = toCell.dot(normalizedForward);
            if (forwardDist < 0.0D || forwardDist > depth) {
                continue;
            }

            double sideDist = Math.abs(toCell.dot(right));
            double upDist = Math.abs(toCell.dot(up));
            if (sideDist > halfWidth || upDist > halfHeight) {
                continue;
            }

            float depthFalloff = (float) (1.0D - (forwardDist / depth));
            float sideFalloff = (float) (1.0D - (sideDist / halfWidth));
            float upFalloff = (float) (1.0D - (upDist / halfHeight));
            float falloff = Mth.clamp(depthFalloff * sideFalloff * upFalloff, 0.0F, 1.0F);
            setDensity(pos, existing - (strength * falloff));
        }
    }

    public void disperseCone(Vec3 origin, Vec3 forward, double length, double halfAngleDegrees, float strength) {
        if (length <= 0.0D) {
            return;
        }

        Vec3 normalizedForward = normalizeOrDefault(forward, new Vec3(0.0D, 0.0D, 1.0D));
        double clampedHalfAngle = Mth.clamp(halfAngleDegrees, 1.0D, 89.0D);
        double cosHalfAngle = Math.cos(Math.toRadians(clampedHalfAngle));

        List<CloudCellPos> cells = List.copyOf(this.densityByCell.keySet());
        for (CloudCellPos pos : cells) {
            float existing = this.densityByCell.getOrDefault(pos, 0.0F);
            if (existing <= 0.0F) {
                continue;
            }

            Vec3 toCell = pos.toWorldCenter(this.origin, this.cellSize).subtract(origin);
            double distSqr = toCell.lengthSqr();
            if (distSqr <= 0.000001D) {
                continue;
            }
            double dist = Math.sqrt(distSqr);
            if (dist > length) {
                continue;
            }

            double alignment = toCell.scale(1.0D / dist).dot(normalizedForward);
            if (alignment < cosHalfAngle) {
                continue;
            }

            float radialFalloff = (float) (1.0D - (dist / length));
            float angleFalloff = (float) ((alignment - cosHalfAngle) / (1.0D - cosHalfAngle));
            float falloff = Mth.clamp(radialFalloff * angleFalloff, 0.0F, 1.0F);
            setDensity(pos, existing - (strength * falloff));
        }
    }

    public int simulateStep() {
        this.ttlTicks--;
        if (this.ttlTicks <= 0 || this.densityByCell.isEmpty()) {
            return 0;
        }

        int changed = 0;
        if (shouldApplyPassiveDecay()) {
            changed += applyPassiveDecay();
        }
        if (this.mode == CloudMode.DIFFUSE) {
            changed += applyDiffuseStep();
        } else {
            changed += applyFloodFillStep();
        }

        return changed;
    }

    public List<CloudCellDelta> drainDirtyDeltas() {
        if (this.dirtyCells.isEmpty()) {
            return List.of();
        }

        List<CloudCellDelta> deltas = new ArrayList<>(this.dirtyCells.size());
        for (CloudCellPos cell : this.dirtyCells) {
            deltas.add(new CloudCellDelta(cell, this.densityByCell.getOrDefault(cell, 0.0F)));
        }
        this.dirtyCells.clear();
        return deltas;
    }

    public int dirtyCellCount() {
        return this.dirtyCells.size();
    }

    private int applyPassiveDecay() {
        int changed = 0;
        float decay = CloudSimConfig.passiveDecayPerStep();
        if (decay <= 0.0F) {
            return changed;
        }

        List<CloudCellPos> cells = List.copyOf(this.densityByCell.keySet());
        for (CloudCellPos cell : cells) {
            float current = this.densityByCell.getOrDefault(cell, 0.0F);
            float next = current - decay;
            if (Math.abs(current - next) > 0.0001F) {
                setDensity(cell, next);
                changed++;
            }
        }
        return changed;
    }

    private boolean shouldApplyPassiveDecay() {
        // Keep clouds stable for most of their lifetime so authored lifetime values are meaningful.
        int decayWindow = Math.max(20, this.initialTtlTicks / 4);
        return this.ttlTicks <= decayWindow;
    }

    private int applyDiffuseStep() {
        float diffusion = CloudSimConfig.diffusionFactor();
        if (diffusion <= 0.0F || this.densityByCell.isEmpty()) {
            return 0;
        }

        int budget = CloudSimConfig.maxCellChangesPerTick();
        int changed = 0;
        Map<CloudCellPos, Float> pending = new HashMap<>();
        List<CloudCellPos> cells = List.copyOf(this.densityByCell.keySet());

        for (CloudCellPos cell : cells) {
            if (changed >= budget) {
                break;
            }

            float baseDensity = this.densityByCell.getOrDefault(cell, 0.0F);
            if (baseDensity <= DENSITY_EPSILON) {
                continue;
            }

            float transfer = (baseDensity * diffusion) / DIFFUSION_NEIGHBORS.size();
            if (transfer <= DENSITY_EPSILON) {
                continue;
            }

            pending.merge(cell, -transfer * DIFFUSION_NEIGHBORS.size(), Float::sum);
            for (CloudCellPos neighborOffset : DIFFUSION_NEIGHBORS) {
                CloudCellPos neighbor = new CloudCellPos(
                        cell.x() + neighborOffset.x(),
                        cell.y() + neighborOffset.y(),
                        cell.z() + neighborOffset.z()
                );
                pending.merge(neighbor, transfer, Float::sum);
            }
            changed++;
        }

        for (Map.Entry<CloudCellPos, Float> entry : pending.entrySet()) {
            CloudCellPos cell = entry.getKey();
            float current = this.densityByCell.getOrDefault(cell, 0.0F);
            setDensity(cell, current + entry.getValue());
        }

        return changed;
    }

    private int applyFloodFillStep() {
        int budget = CloudSimConfig.maxFloodFillExpansionsPerTick();
        int changed = 0;
        float addAmount = 0.85F;

        while (!this.floodQueue.isEmpty() && changed < budget) {
            CloudCellPos current = this.floodQueue.pollFirst();
            this.floodQueued.remove(current);
            if (!canOccupy(current)) {
                continue;
            }

            float currentDensity = this.densityByCell.getOrDefault(current, 0.0F);
            setDensity(current, Math.max(currentDensity, addAmount));
            changed++;

            for (CloudCellPos neighborOffset : DIFFUSION_NEIGHBORS) {
                CloudCellPos neighbor = new CloudCellPos(
                        current.x() + neighborOffset.x(),
                        current.y() + neighborOffset.y(),
                        current.z() + neighborOffset.z()
                );

                if (this.densityByCell.size() >= CloudSimConfig.maxCellsPerVolume()) {
                    break;
                }

                if (!canOccupy(neighbor)) {
                    continue;
                }

                if (this.floodQueued.add(neighbor)) {
                    this.floodQueue.addLast(neighbor);
                }
            }
        }

        return changed;
    }

    private void applyRadial(Vec3 center, double radius, DensityOperator operator) {
        if (radius <= 0.0D) {
            return;
        }

        int radiusCells = Math.max(1, (int) Math.ceil(radius / this.cellSize));
        CloudCellPos centerCell = CloudCellPos.fromWorld(center, this.cellSize, this.origin);
        for (int dx = -radiusCells; dx <= radiusCells; dx++) {
            for (int dy = -radiusCells; dy <= radiusCells; dy++) {
                for (int dz = -radiusCells; dz <= radiusCells; dz++) {
                    double dist = Math.sqrt((dx * dx) + (dy * dy) + (dz * dz)) * this.cellSize;
                    if (dist > radius) {
                        continue;
                    }

                    CloudCellPos target = new CloudCellPos(centerCell.x() + dx, centerCell.y() + dy, centerCell.z() + dz);
                    float currentDensity = this.densityByCell.getOrDefault(target, 0.0F);
                    float falloff = radius <= 0.001D ? 1.0F : CloudDispersalMath.radialFalloff(dist, radius);
                    setDensity(target, operator.apply(currentDensity, Mth.clamp(falloff, 0.0F, 1.0F)));
                }
            }
        }
    }

    private void setDensity(CloudCellPos pos, float density) {
        float clamped = Mth.clamp(density, 0.0F, 1.0F);
        if (clamped <= DENSITY_EPSILON) {
            if (this.densityByCell.remove(pos) != null) {
                this.dirtyCells.add(pos);
            }
            return;
        }

        if (!canOccupy(pos)) {
            return;
        }

        if (!this.densityByCell.containsKey(pos) && this.densityByCell.size() >= CloudSimConfig.maxCellsPerVolume()) {
            return;
        }

        Float previous = this.densityByCell.put(pos, clamped);
        if (previous == null || Math.abs(previous - clamped) > 0.0001F) {
            this.dirtyCells.add(pos);
        }
    }

    @FunctionalInterface
    private interface DensityOperator {
        float apply(float currentDensity, float falloff);
    }

    private boolean canOccupy(CloudCellPos pos) {
        Vec3 center = pos.toWorldCenter(this.origin, this.cellSize);
        BlockPos blockPos = BlockPos.containing(center);
        BlockState state = this.level.getBlockState(blockPos);
        return !state.blocksMotion();
    }

    private static Vec3 normalizeOrDefault(Vec3 value, Vec3 defaultValue) {
        if (value.lengthSqr() < 0.000001D) {
            return defaultValue;
        }
        return value.normalize();
    }
}
