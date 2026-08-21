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
    private final boolean combinationOnly;
    private final boolean ignorePlayerDNA;
    private final CombinationRecipe combinationRecipe;
    private final List<String> mobs;
    private final List<AttributeEffect> attributeEffects;
    private final List<ResistanceEffect> resistanceEffects;

    public GeneType(
            String id,
            GeneCategory category,
            GeneRarity rarity,
            String description,
            int qualityMin,
            int qualityMax,
            boolean combinable,
            CombinationRecipe combinationRecipe,
            List<String> mobs,
            List<AttributeEffect> attributeEffects,
            List<ResistanceEffect> resistanceEffects,
            boolean combinationOnly,
            boolean ignorePlayerDNA
    ) {
        this.id = Objects.requireNonNull(id, "id cannot be null");
        this.category = Objects.requireNonNull(category, "category cannot be null");
        this.rarity = Objects.requireNonNull(rarity, "rarity cannot be null");
        this.description = description == null ? "" : description;
        this.qualityMin = qualityMin;
        this.qualityMax = qualityMax;
        this.combinable = combinable;
        this.combinationOnly = combinationOnly;
        this.ignorePlayerDNA = ignorePlayerDNA;
        this.combinationRecipe = combinationRecipe;
        this.mobs = mobs != null ? new ArrayList<>(mobs) : new ArrayList<>();
        this.attributeEffects = attributeEffects != null ? new ArrayList<>(attributeEffects) : new ArrayList<>();
        this.resistanceEffects = resistanceEffects != null ? new ArrayList<>(resistanceEffects) : new ArrayList<>();
    }

    public GeneType(
            String id,
            GeneCategory category,
            GeneRarity rarity,
            String description,
            int qualityMin,
            int qualityMax,
            boolean combinable,
            CombinationRecipe combinationRecipe,
            List<String> mobs,
            List<AttributeEffect> attributeEffects
    ) {
        this(id, category, rarity, description, qualityMin, qualityMax, combinable, combinationRecipe, mobs, attributeEffects, Collections.emptyList(), false, false);
    }

    public GeneType(
            String id,
            GeneCategory category,
            GeneRarity rarity,
            String description,
            int qualityMin,
            int qualityMax,
            boolean combinable,
            CombinationRecipe combinationRecipe,
            List<String> mobs,
            List<AttributeEffect> attributeEffects,
            boolean combinationOnly
    ) {
        this(id, category, rarity, description, qualityMin, qualityMax, combinable, combinationRecipe, mobs, attributeEffects, Collections.emptyList(), combinationOnly, false);
    }

    public GeneType(
            String id,
            GeneCategory category,
            GeneRarity rarity,
            String description,
            int qualityMin,
            int qualityMax,
            boolean combinable,
            CombinationRecipe combinationRecipe,
            List<String> mobs,
            List<AttributeEffect> attributeEffects,
            List<ResistanceEffect> resistanceEffects,
            boolean combinationOnly
    ) {
        this(id, category, rarity, description, qualityMin, qualityMax, combinable, combinationRecipe, mobs, attributeEffects, resistanceEffects, combinationOnly, false);
    }

    public GeneType(String id, String description, int qualityMin, int qualityMax) {
        this(id, GeneCategory.ATTRIBUTE, GeneRarity.COMMON, description, qualityMin, qualityMax, false, null, Collections.emptyList(), null);
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

    public boolean isCombinationOnly() {
        return this.combinationOnly;
    }

    public boolean isIgnoredForPlayerDNA() {
        return this.ignorePlayerDNA;
    }

    public CombinationRecipe getCombinationRecipe() {
        return this.combinationRecipe;
    }

    public List<String> getMobs() {
        return Collections.unmodifiableList(this.mobs);
    }

    public List<AttributeEffect> getAttributeEffects() {
        return Collections.unmodifiableList(this.attributeEffects);
    }

    public List<ResistanceEffect> getResistanceEffects() {
        return Collections.unmodifiableList(this.resistanceEffects);
    }

    public boolean canAppearInMob(String mobId) {
        if (this.combinationOnly) {
            return false;
        }
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
        private final List<BuilderRequirement> builderRequirements;
        private final int successRate;

        public CombinationRecipe(List<Requirement> requirements, List<BuilderRequirement> builderRequirements, int successRate) {
            this.requirements = requirements != null ? new ArrayList<>(requirements) : new ArrayList<>();
            this.builderRequirements = builderRequirements != null ? new ArrayList<>(builderRequirements) : new ArrayList<>();
            this.successRate = successRate;
        }

        public List<Requirement> getRequirements() {
            return Collections.unmodifiableList(this.requirements);
        }

        public List<BuilderRequirement> getBuilderRequirements() {
            return Collections.unmodifiableList(this.builderRequirements);
        }

        public int getSuccessRate() {
            return this.successRate;
        }
    }

    public static final class BuilderRequirement {
        private final int count;
        private final int minQuality;
        private final GeneRarity rarity;

        public BuilderRequirement(int count, int minQuality, GeneRarity rarity) {
            this.count = count;
            this.minQuality = minQuality;
            this.rarity = rarity;
        }

        public int getCount() {
            return this.count;
        }

        public int getMinQuality() {
            return this.minQuality;
        }

        public GeneRarity getRarity() {
            return this.rarity;
        }
    }

    public static final class AttributeEffect {
        private final String attributeId;
        private final double minModifier;
        private final double maxModifier;

        public AttributeEffect(String attributeId, double minModifier, double maxModifier) {
            this.attributeId = Objects.requireNonNull(attributeId, "attributeId cannot be null");
            this.minModifier = minModifier;
            this.maxModifier = maxModifier;
        }

        public String getAttributeId() {
            return this.attributeId;
        }

        public double getMinModifier() {
            return this.minModifier;
        }

        public double getMaxModifier() {
            return this.maxModifier;
        }

        public double resolveModifierForQuality(int quality, int qualityMin, int qualityMax) {
            return interpolateByQuality(this.minModifier, this.maxModifier, quality, qualityMin, qualityMax);
        }
    }

    public enum ResistanceKind {
        FIRE_TICK_DAMAGE,
        POISON_DAMAGE_AVOIDANCE,
        WITHER_NULLIFY
    }

    public static final class ResistanceEffect {
        private final ResistanceKind kind;
        private final double minValue;
        private final double maxValue;

        public ResistanceEffect(ResistanceKind kind, double minValue, double maxValue) {
            this.kind = Objects.requireNonNull(kind, "kind cannot be null");
            this.minValue = minValue;
            this.maxValue = maxValue;
        }

        public ResistanceKind getKind() {
            return this.kind;
        }

        public double getMinValue() {
            return this.minValue;
        }

        public double getMaxValue() {
            return this.maxValue;
        }

        public double resolveValueForQuality(int quality, int qualityMin, int qualityMax) {
            return interpolateByQuality(this.minValue, this.maxValue, quality, qualityMin, qualityMax);
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

    private static double interpolateByQuality(double minValue, double maxValue, int quality, int qualityMin, int qualityMax) {
        if (qualityMax <= qualityMin) {
            return maxValue;
        }
        double clampedQuality = Math.max(qualityMin, Math.min(qualityMax, quality));
        double qualityProgress = (clampedQuality - qualityMin) / (double) (qualityMax - qualityMin);
        return minValue + (maxValue - minValue) * qualityProgress;
    }
}