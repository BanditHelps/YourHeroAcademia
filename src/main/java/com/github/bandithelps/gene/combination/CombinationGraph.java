package com.github.bandithelps.gene.combination;

import com.github.bandithelps.gene.GeneCategory;
import com.github.bandithelps.gene.GeneRarity;
import com.github.bandithelps.gene.GeneType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.Collectors;

public final class CombinationGraph {
    private final long worldSeed;
    private final Map<String, ResolvedCombinationRecipe> recipesByOutput;
    private final Map<String, List<String>> overlapGroups;

    private CombinationGraph(
            long worldSeed,
            Map<String, ResolvedCombinationRecipe> recipesByOutput,
            Map<String, List<String>> overlapGroups
    ) {
        this.worldSeed = worldSeed;
        this.recipesByOutput = recipesByOutput;
        this.overlapGroups = overlapGroups;
    }

    public long getWorldSeed() {
        return this.worldSeed;
    }

    public static CombinationGraph empty() {
        return new CombinationGraph(0L, Collections.emptyMap(), Collections.emptyMap());
    }

    public static CombinationGraph generate(long worldSeed, List<GeneType> allGeneTypes) {
        List<GeneType> safeTypes = allGeneTypes == null ? List.of() : allGeneTypes;
        List<GeneType> builderPool = safeTypes.stream()
                .filter(type -> type.getCategory() == GeneCategory.BUILDER)
                .sorted(Comparator.comparing(GeneType::getId, String::compareToIgnoreCase))
                .toList();

        List<GeneType> combinable = safeTypes.stream()
                .filter(GeneType::isCombinable)
                .sorted(Comparator.comparing(GeneType::getId, String::compareToIgnoreCase))
                .toList();

        Map<String, ResolvedCombinationRecipe> resolved = new LinkedHashMap<>();
        for (GeneType output : combinable) {
            GeneType.CombinationRecipe sourceRecipe = output.getCombinationRecipe();
            String outputId = output.getId().toLowerCase(Locale.ROOT);
            if (sourceRecipe == null) {
                resolved.put(outputId, new ResolvedCombinationRecipe(
                        outputId,
                        100,
                        List.of(),
                        false,
                        "Combinable gene is missing combination data."
                ));
                continue;
            }

            List<ResolvedCombinationRecipe.ResolvedRequirement> requirements = new ArrayList<>();
            for (GeneType.Requirement sourceRequirement : sourceRecipe.getRequirements()) {
                requirements.add(new ResolvedCombinationRecipe.ResolvedRequirement(
                        sourceRequirement.getId().toLowerCase(Locale.ROOT),
                        Math.max(1, sourceRequirement.getMinQuality()),
                        false
                ));
            }

            BuilderResolution builderResolution = resolveBuilderIds(
                    worldSeed,
                    outputId,
                    builderPool,
                    sourceRecipe.getBuilderRequirements()
            );
            if (!builderResolution.valid()) {
                resolved.put(outputId, new ResolvedCombinationRecipe(
                        outputId,
                        sourceRecipe.getSuccessRate(),
                        requirements,
                        false,
                        builderResolution.invalidReason()
                ));
                continue;
            }
            requirements.addAll(builderResolution.resolvedRequirements());

            resolved.put(outputId, new ResolvedCombinationRecipe(
                    outputId,
                    sourceRecipe.getSuccessRate(),
                    requirements,
                    true,
                    ""
            ));
        }

        Map<String, List<String>> overlapGroups = detectOverlapGroups(resolved.values());
        return new CombinationGraph(worldSeed, Collections.unmodifiableMap(resolved), Collections.unmodifiableMap(overlapGroups));
    }

    private static BuilderResolution resolveBuilderIds(
            long worldSeed,
            String outputGeneId,
            List<GeneType> builderPool,
            List<GeneType.BuilderRequirement> builderRequirements
    ) {
        if (builderRequirements == null || builderRequirements.isEmpty()) {
            return BuilderResolution.success(List.of());
        }
        if (builderPool.isEmpty()) {
            return BuilderResolution.invalid("Builder requirement exists but no builder genes are registered.");
        }

        long salt = outputGeneId.toLowerCase(Locale.ROOT).hashCode();
        Random random = new Random(worldSeed ^ (salt * 341873128712L));
        Set<String> usedBuilderIds = new HashSet<>();
        List<ResolvedCombinationRecipe.ResolvedRequirement> resolved = new ArrayList<>();

        for (GeneType.BuilderRequirement builderRequirement : builderRequirements) {
            int count = Math.max(0, builderRequirement.getCount());
            int minQuality = Math.max(1, builderRequirement.getMinQuality());
            if (count <= 0) {
                continue;
            }
            List<GeneType> candidates = builderPool.stream()
                    .filter(type -> !usedBuilderIds.contains(type.getId().toLowerCase(Locale.ROOT)))
                    .filter(type -> matchesRarity(type, builderRequirement.getRarity()))
                    .collect(Collectors.toCollection(ArrayList::new));
            if (count > candidates.size()) {
                String rarityText = builderRequirement.getRarity() == null
                        ? "any rarity"
                        : builderRequirement.getRarity().name();
                return BuilderResolution.invalid(
                        "Builder requirement exceeds registered builder pool for rarity " + rarityText + "."
                );
            }

            for (int i = 0; i < count; i++) {
                int index = random.nextInt(candidates.size());
                GeneType picked = candidates.remove(index);
                String builderId = picked.getId().toLowerCase(Locale.ROOT);
                usedBuilderIds.add(builderId);
                resolved.add(new ResolvedCombinationRecipe.ResolvedRequirement(
                        builderId,
                        minQuality,
                        true
                ));
            }
        }

        return BuilderResolution.success(resolved);
    }

    private static boolean matchesRarity(GeneType type, GeneRarity requiredRarity) {
        if (requiredRarity == null) {
            return true;
        }
        return type.getRarity() == requiredRarity;
    }

    private record BuilderResolution(
            boolean valid,
            List<ResolvedCombinationRecipe.ResolvedRequirement> resolvedRequirements,
            String invalidReason
    ) {
        private static BuilderResolution success(List<ResolvedCombinationRecipe.ResolvedRequirement> resolvedRequirements) {
            return new BuilderResolution(true, resolvedRequirements, "");
        }

        private static BuilderResolution invalid(String invalidReason) {
            return new BuilderResolution(false, List.of(), invalidReason);
        }
    }

    private static Map<String, List<String>> detectOverlapGroups(Iterable<ResolvedCombinationRecipe> recipes) {
        Map<String, List<String>> bySignature = new HashMap<>();
        for (ResolvedCombinationRecipe recipe : recipes) {
            if (!recipe.isValid()) {
                continue;
            }
            bySignature.computeIfAbsent(recipe.normalizedRequirementSignature(), key -> new ArrayList<>())
                    .add(recipe.getOutputGeneId());
        }
        return bySignature.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> {
                            List<String> outputs = new ArrayList<>(entry.getValue());
                            outputs.sort(String::compareToIgnoreCase);
                            return outputs;
                        }
                ));
    }

    public List<ResolvedCombinationRecipe> getAllRecipes() {
        return List.copyOf(this.recipesByOutput.values());
    }

    public List<ResolvedCombinationRecipe> getValidRecipes() {
        return this.recipesByOutput.values().stream()
                .filter(ResolvedCombinationRecipe::isValid)
                .toList();
    }

    public ResolvedCombinationRecipe getRecipe(String outputGeneId) {
        if (outputGeneId == null || outputGeneId.isBlank()) {
            return null;
        }
        return this.recipesByOutput.get(outputGeneId.toLowerCase(Locale.ROOT));
    }

    public Map<String, List<String>> getOverlapGroups() {
        return this.overlapGroups;
    }

    public int getInvalidCount() {
        int count = 0;
        for (ResolvedCombinationRecipe recipe : this.recipesByOutput.values()) {
            if (!recipe.isValid()) {
                count++;
            }
        }
        return count;
    }
}
