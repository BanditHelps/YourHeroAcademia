package com.github.bandithelps.gene;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class DNA {
    private final String sourceName;
    private final UUID sourceUuid;
    private final List<Gene> genes;
    private final long harvestTime;

    public DNA(String sourceName, UUID sourceUuid, List<Gene> genes, long harvestTime) {
        this.sourceName = Objects.requireNonNull(sourceName, "sourceName cannot be null");
        this.sourceUuid = Objects.requireNonNull(sourceUuid, "sourceUuid cannot be null");
        this.genes = genes != null ? new ArrayList<>(genes) : new ArrayList<>();
        this.harvestTime = harvestTime;
    }

    public DNA(String sourceName, UUID sourceUuid, List<Gene> genes) {
        this(sourceName, sourceUuid, genes, System.currentTimeMillis());
    }

    public String getSourceName() {
        return this.sourceName;
    }

    public UUID getSourceUuid() {
        return this.sourceUuid;
    }

    public List<Gene> getGenes() {
        return new ArrayList<>(this.genes);
    }

    public long getHarvestTime() {
        return this.harvestTime;
    }

    public int getGeneCount() {
        return this.genes.size();
    }

    public boolean isEmpty() {
        return this.genes.isEmpty();
    }

    public String toString() {
        return "DNA{" +
                "sourceName='" + this.sourceName + '\'' +
                ", sourceUuid=" + this.sourceUuid +
                ", geneCount=" + this.genes.size() +
                '}';
    }
}