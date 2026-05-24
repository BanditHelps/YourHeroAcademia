package com.github.bandithelps.gene.combination;

import com.github.bandithelps.YourHeroAcademia;
import com.mojang.serialization.Codec;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

public final class CombinationGraphSavedData extends SavedData {
    public static final SavedDataType<CombinationGraphSavedData> ID = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(YourHeroAcademia.MODID, "combination_graph"),
            CombinationGraphSavedData::new,
            Codec.unboundedMap(Codec.STRING, Codec.STRING)
                    .xmap(CombinationGraphSavedData::fromStoredMap, CombinationGraphSavedData::toStoredMap)
    );

    private long worldSeed;
    private int recipeCount;
    private int invalidRecipeCount;
    private int overlapCount;
    private final Map<String, String> resolvedBuildersByOutput = new HashMap<>();

    public static CombinationGraphSavedData get(ServerLevel level) {
        return level.getServer().getDataStorage().computeIfAbsent(ID);
    }

    public CombinationGraphSavedData() {
    }

    private static CombinationGraphSavedData fromStoredMap(Map<String, String> stored) {
        CombinationGraphSavedData data = new CombinationGraphSavedData();
        if (stored == null) {
            return data;
        }
        data.worldSeed = parseLong(stored.getOrDefault("__seed", "0"));
        data.recipeCount = (int) parseLong(stored.getOrDefault("__count", "0"));
        data.invalidRecipeCount = (int) parseLong(stored.getOrDefault("__invalid", "0"));
        data.overlapCount = (int) parseLong(stored.getOrDefault("__overlaps", "0"));
        for (Map.Entry<String, String> entry : stored.entrySet()) {
            if (entry.getKey().startsWith("__")) {
                continue;
            }
            data.resolvedBuildersByOutput.put(entry.getKey(), entry.getValue());
        }
        return data;
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private Map<String, String> toStoredMap() {
        Map<String, String> stored = new HashMap<>(this.resolvedBuildersByOutput);
        stored.put("__seed", Long.toString(this.worldSeed));
        stored.put("__count", Integer.toString(this.recipeCount));
        stored.put("__invalid", Integer.toString(this.invalidRecipeCount));
        stored.put("__overlaps", Integer.toString(this.overlapCount));
        return stored;
    }

    public void updateFromGraph(CombinationGraph graph) {
        if (graph == null) {
            return;
        }
        this.worldSeed = graph.getWorldSeed();
        this.recipeCount = graph.getAllRecipes().size();
        this.invalidRecipeCount = graph.getInvalidCount();
        this.overlapCount = graph.getOverlapGroups().size();
        this.resolvedBuildersByOutput.clear();
        for (ResolvedCombinationRecipe recipe : graph.getAllRecipes()) {
            String builders = recipe.getRequirements().stream()
                    .filter(ResolvedCombinationRecipe.ResolvedRequirement::builderResolved)
                    .map(ResolvedCombinationRecipe.ResolvedRequirement::geneId)
                    .sorted(String::compareToIgnoreCase)
                    .reduce((a, b) -> a + "," + b)
                    .orElse("");
            this.resolvedBuildersByOutput.put(recipe.getOutputGeneId(), builders);
        }
        setDirty();
    }

    public long getWorldSeed() {
        return this.worldSeed;
    }

    public int getRecipeCount() {
        return this.recipeCount;
    }

    public int getInvalidRecipeCount() {
        return this.invalidRecipeCount;
    }

    public int getOverlapCount() {
        return this.overlapCount;
    }

    public Map<String, String> getResolvedBuildersByOutput() {
        return Collections.unmodifiableMap(this.resolvedBuildersByOutput);
    }
}
