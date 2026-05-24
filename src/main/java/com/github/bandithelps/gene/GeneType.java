package com.github.bandithelps.gene;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class GeneType {

    private final String id;
    private final GeneCategory category;
    private final GeneRarity rarity;
    private final String description;
    private final int qualityMin;
    private final int qualityMax;
    private final boolean combinable;
    private final CombinationRecipe combinationRecipe;
    private final List<String> mobs;

    public GeneType(
            String id,
            GeneCategory category,
            GeneRarity rarity,
            String description,
            int qualityMin,
            int qualityMax,
            boolean combinable,
            CombinationRecipe combinationRecipe,
            List<String> mobs
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.rarity = Objects.requireNonNull(rarity, "rarity cannot be null");
        this.description = description == null ? "" : description;
        this.qualityMin = qualityMin;
        this.qualityMax = qualityMax;
        this.combinable = combinable;
        this.combinationRecipe = combinationRecipe;
        this.mobs = mobs != null ? new ArrayList<>(mobs) : new ArrayList<>();
    }

    public GeneType(String id, String description, int qualityMin, int qualityMax) {
        this(id, GeneCategory.ATTRIBUTE, GeneRarity.COMMON, description, qualityMin, qualityMax, false, null, Collections.emptyList());
    }

    public GeneType(String id, String description) {
        this(id, description, 1, 100);
    }

    public String getId() {
        return this.id;
    }

    public GeneCategory getCategory() {
        return this.category;
    }

    public GeneRarity getRarity() {
        return this.rarity;
    }

    public String getDescription() {
        return this.description;
    }

    public int getQualityMin() {
        return this.qualityMin;
    }

    public int getQualityMax() {
        return this.qualityMax;
    }

    public boolean isCombinable() {
        return this.combinable;
    }

    public CombinationRecipe getCombinationRecipe() {
        return this.combinationRecipe;
    }

    public List<String> getMobs() {
        return Collections.unmodifiableList(this.mobs);
    }

    public boolean canAppearInMob(String mobId) {
        if (this.mobs.isEmpty()) {
            return true;
        }
        if (mobId == null || mobId.isEmpty()) {
            return false;
        }
        return this.mobs.stream().anyMatch(mobId::equalsIgnoreCase);
    }

    public String toString() {
        return this.id;
    }

    public static final class CombinationRecipe {
        private final List<Requirement> requirements;
        private final int builderCount;
        private final int builderMinQuality;
        private final int successRate;

        public CombinationRecipe(List<Requirement> requirements, int builderCount, int builderMinQuality, int successRate) {
            this.requirements = requirements != null ? new ArrayList<>(requirements) : new ArrayList<>();
            this.builderCount = builderCount;
            this.builderMinQuality = builderMinQuality;
            this.successRate = successRate;
        }

        public List<Requirement> getRequirements() {
            return Collections.unmodifiableList(this.requirements);
        }

        public int getBuilderCount() {
            return this.builderCount;
        }

        public int getBuilderMinQuality() {
            return this.builderMinQuality;
        }

        public int getSuccessRate() {
            return this.successRate;
        }
    }

    public static final class Requirement {
        private final String id;
        private final int minQuality;

        public Requirement(String id, int minQuality) {
            this.id = Objects.requireNonNull(id, "id cannot be null");
            this.minQuality = minQuality;
        }

        public String getId() {
            return this.id;
        }

        public int getMinQuality() {
            return this.minQuality;
        }
    }
}