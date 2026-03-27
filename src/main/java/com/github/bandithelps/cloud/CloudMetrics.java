package com.github.bandithelps.cloud;

public final class CloudMetrics {
    private long simulationNanos;
    private int simulatedVolumeCount;
    private int changedCellCount;
    private int sentPacketCount;
    private int pendingDeltaCellCount;

    public void resetFrame() {
        this.simulationNanos = 0L;
        this.simulatedVolumeCount = 0;
        this.changedCellCount = 0;
        this.sentPacketCount = 0;
        this.pendingDeltaCellCount = 0;
    }

    public void addSimulationNanos(long nanos) {
        this.simulationNanos += Math.max(0L, nanos);
    }

    public void incrementVolumes() {
        this.simulatedVolumeCount++;
    }

    public void addChangedCells(int count) {
        this.changedCellCount += Math.max(0, count);
    }

    public void addSentPackets(int count) {
        this.sentPacketCount += Math.max(0, count);
    }

    public void setPendingDeltaCellCount(int pendingDeltaCellCount) {
        this.pendingDeltaCellCount = Math.max(0, pendingDeltaCellCount);
    }

    public long simulationNanos() {
        return this.simulationNanos;
    }

    public int simulatedVolumeCount() {
        return this.simulatedVolumeCount;
    }

    public int changedCellCount() {
        return this.changedCellCount;
    }

    public int sentPacketCount() {
        return this.sentPacketCount;
    }

    public int pendingDeltaCellCount() {
        return this.pendingDeltaCellCount;
    }
}
