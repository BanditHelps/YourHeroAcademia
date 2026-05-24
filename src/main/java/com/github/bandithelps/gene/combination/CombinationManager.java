package com.github.bandithelps.gene.combination;

import com.github.bandithelps.YourHeroAcademia;
import com.github.bandithelps.gene.Gene;
import com.github.bandithelps.gene.GeneRegistry;
import com.github.bandithelps.gene.GeneType;
import com.github.bandithelps.gene.SideEffect;
import com.github.bandithelps.utils.gene.GeneUtil;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

public final class CombinationManager {
    private static final int OUTPUT_VIAL_CAPACITY = 3;
    private static volatile CombinationGraph activeGraph = CombinationGraph.empty();

    private CombinationManager() {
    }

    public static CombinationGraph getGraph() {
        return activeGraph;
    }

    public static CombinationGraph rebuildForServer(MinecraftServer server) {
        if (server == null) {
            activeGraph = CombinationGraph.empty();
            return activeGraph;
        }
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            activeGraph = CombinationGraph.empty();
            return activeGraph;
        }
        return rebuildForLevel(overworld);
    }

    public static CombinationGraph rebuildForLevel(ServerLevel level) {
        if (level == null) {
            activeGraph = CombinationGraph.empty();
            return activeGraph;
        }
        CombinationGraph graph = CombinationGraph.generate(level.getSeed(), GeneRegistry.getInstance().getAllGeneTypes());
        activeGraph = graph;
        CombinationGraphSavedData.get(level).updateFromGraph(graph);
        if (!graph.getOverlapGroups().isEmpty()) {
            YourHeroAcademia.LOGGER.warn("Detected {} overlapping combination signatures. Multiple outputs may result from one input set.", graph.getOverlapGroups().size());
        }
        return graph;
    }

    public static CombinationAttemptResult evaluateAndRoll(List<String> serializedGenes, Random random) {
        List<Gene> inputGenes = new ArrayList<>();
        if (serializedGenes != null) {
            for (String raw : serializedGenes) {
                Gene gene = GeneUtil.parseGene(raw);
                if (gene != null) {
                    inputGenes.add(gene);
                }
            }
        }

        if (inputGenes.isEmpty()) {
            return CombinationAttemptResult.noMatch();
        }

        List<ResolvedCombinationRecipe> matchedRecipes = findMatches(inputGenes, activeGraph.getValidRecipes());
        if (matchedRecipes.isEmpty()) {
            return CombinationAttemptResult.noMatch();
        }

        Random safeRandom = random == null ? new Random() : random;
        List<Gene> producedGenes = new ArrayList<>();
        for (ResolvedCombinationRecipe recipe : matchedRecipes) {
            int roll = safeRandom.nextInt(100) + 1;
            if (roll > recipe.getSuccessRate()) {
                continue;
            }
            GeneType outputType = GeneRegistry.getInstance().getGeneType(recipe.getOutputGeneId()).orElse(null);
            if (outputType == null) {
                continue;
            }
            int qualityRange = Math.max(1, outputType.getQualityMax() - outputType.getQualityMin() + 1);
            int quality = outputType.getQualityMin() + safeRandom.nextInt(qualityRange);
            EnumSet<SideEffect> transferred = EnumSet.noneOf(SideEffect.class);
            for (Gene gene : inputGenes) {
                transferred.addAll(gene.getSideEffects());
            }
            Gene output = new Gene(
                    GeneUtil.generateGeneName(),
                    outputType.getCategory(),
                    outputType,
                    outputType.getDescription(),
                    quality,
                    new ArrayList<>(transferred)
            );
            producedGenes.add(output);
        }

        if (producedGenes.isEmpty()) {
            return CombinationAttemptResult.fail(matchedRecipes);
        }

        boolean overflow = producedGenes.size() > OUTPUT_VIAL_CAPACITY;
        List<Gene> capped = overflow ? producedGenes.subList(0, OUTPUT_VIAL_CAPACITY) : producedGenes;
        return CombinationAttemptResult.success(matchedRecipes, new ArrayList<>(capped), overflow);
    }

    public static List<ResolvedCombinationRecipe> findMatches(List<Gene> inputGenes, List<ResolvedCombinationRecipe> recipes) {
        if (inputGenes == null || inputGenes.isEmpty() || recipes == null || recipes.isEmpty()) {
            return List.of();
        }
        Map<String, List<Gene>> genesByType = new HashMap<>();
        for (Gene gene : inputGenes) {
            genesByType.computeIfAbsent(gene.getType().getId().toLowerCase(Locale.ROOT), key -> new ArrayList<>()).add(gene);
        }

        List<ResolvedCombinationRecipe> matched = new ArrayList<>();
        for (ResolvedCombinationRecipe recipe : recipes) {
            if (!recipe.isValid()) {
                continue;
            }
            if (matchesRecipe(recipe, genesByType)) {
                matched.add(recipe);
            }
        }
        return matched;
    }

    private static boolean matchesRecipe(
            ResolvedCombinationRecipe recipe,
            Map<String, List<Gene>> genesByType
    ) {
        Map<String, Integer> usage = new HashMap<>();
        for (ResolvedCombinationRecipe.ResolvedRequirement requirement : recipe.getRequirements()) {
            String typeId = requirement.geneId().toLowerCase(Locale.ROOT);
            List<Gene> candidates = genesByType.getOrDefault(typeId, List.of());
            int used = usage.getOrDefault(typeId, 0);
            int foundIndex = -1;
            for (int i = 0; i < candidates.size(); i++) {
                if (i < used) {
                    continue;
                }
                if (candidates.get(i).getQuality() >= requirement.minQuality()) {
                    foundIndex = i;
                    break;
                }
            }
            if (foundIndex < 0) {
                return false;
            }
            usage.put(typeId, foundIndex + 1);
        }
        return true;
    }

    public record CombinationAttemptResult(
            boolean hasAnyMatch,
            boolean success,
            boolean overflow,
            List<ResolvedCombinationRecipe> matchedRecipes,
            List<Gene> producedGenes
    ) {
        public static CombinationAttemptResult noMatch() {
            return new CombinationAttemptResult(false, false, false, List.of(), List.of());
        }

        public static CombinationAttemptResult fail(List<ResolvedCombinationRecipe> matchedRecipes) {
            return new CombinationAttemptResult(true, false, false, List.copyOf(matchedRecipes), List.of());
        }

        public static CombinationAttemptResult success(List<ResolvedCombinationRecipe> matchedRecipes, List<Gene> producedGenes, boolean overflow) {
            return new CombinationAttemptResult(true, true, overflow, List.copyOf(matchedRecipes), List.copyOf(producedGenes));
        }
    }
}
