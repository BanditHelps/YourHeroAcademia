package com.github.bandithelps.gene.combination;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ResolvedCombinationRecipe {
    private final String outputGeneId;
    private final int successRate;
    private final List<ResolvedRequirement> requirements;
    private final boolean valid;
    private final String invalidReason;

    public ResolvedCombinationRecipe(
            String outputGeneId,
            int successRate,
            List<ResolvedRequirement> requirements,
            boolean valid,
            String invalidReason
    ) {
        this.outputGeneId = outputGeneId == null ? "" : outputGeneId;
        this.successRate = Math.max(0, Math.min(100, successRate));
        this.requirements = requirements == null
                ? new ArrayList<>()
                : new ArrayList<>(requirements);
        this.valid = valid;
        this.invalidReason = invalidReason == null ? "" : invalidReason;
    }

    public String getOutputGeneId() {
        return this.outputGeneId;
    }

    public int getSuccessRate() {
        return this.successRate;
    }

    public List<ResolvedRequirement> getRequirements() {
        return Collections.unmodifiableList(this.requirements);
    }

    public boolean isValid() {
        return this.valid;
    }

    public String getInvalidReason() {
        return this.invalidReason;
    }

    public String normalizedRequirementSignature() {
        List<String> parts = new ArrayList<>();
        for (ResolvedRequirement requirement : this.requirements) {
            parts.add(requirement.geneId().toLowerCase() + "|" + requirement.minQuality());
        }
        Collections.sort(parts);
        return String.join(",", parts);
    }

    public record ResolvedRequirement(
            String geneId,
            int minQuality,
            boolean builderResolved
    ) {
    }
}
