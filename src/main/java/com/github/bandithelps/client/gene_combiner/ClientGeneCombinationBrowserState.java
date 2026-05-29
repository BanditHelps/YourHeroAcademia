package com.github.bandithelps.client.gene_combiner;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class ClientGeneCombinationBrowserState {
    private static volatile List<RecipeEntry> recipes = List.of();
    private static final Map<String, RecipeEntry> recipesByOutput = new ConcurrentHashMap<>();

    private ClientGeneCombinationBrowserState() {
    }

    public static void setRecipesFromPayload(List<String> encodedRecipes) {
        if (encodedRecipes == null || encodedRecipes.isEmpty()) {
            clear();
            return;
        }

        List<RecipeEntry> next = encodedRecipes.stream()
                .map(ClientGeneCombinationBrowserState::decodeRecipe)
                .filter(Objects::nonNull)
                .toList();

        recipes = List.copyOf(next);
        recipesByOutput.clear();
        for (RecipeEntry recipe : recipes) {
            recipesByOutput.put(normalizeGeneId(recipe.outputGeneId()), recipe);
        }
    }

    public static List<RecipeEntry> getRecipes() {
        return recipes;
    }

    public static void setLines(List<String> encodedRecipes) {
        setRecipesFromPayload(encodedRecipes);
    }

    public static List<String> getLines() {
        return recipes.stream()
                .map(recipe -> recipe.outputGeneId() + " <- " + recipe.requirements().size() + " requirements")
                .toList();
    }

    public static RecipeEntry getRecipe(String geneId) {
        if (geneId == null || geneId.isBlank()) {
            return null;
        }
        return recipesByOutput.get(normalizeGeneId(geneId));
    }

    public static void clear() {
        recipes = List.of();
        recipesByOutput.clear();
    }

    private static String normalizeGeneId(String geneId) {
        return geneId == null ? "" : geneId.toLowerCase(Locale.ROOT);
    }

    private static RecipeEntry decodeRecipe(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            JsonObject root = JsonParser.parseString(encoded).getAsJsonObject();
            String outputId = getString(root, "outputId");
            if (outputId.isBlank()) {
                return null;
            }

            List<RequirementEntry> requirements = List.of();
            JsonArray requirementsArray = root.getAsJsonArray("requirements");
            if (requirementsArray != null && !requirementsArray.isEmpty()) {
                java.util.ArrayList<RequirementEntry> decoded = new java.util.ArrayList<>();
                for (JsonElement element : requirementsArray) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    JsonObject req = element.getAsJsonObject();
                    decoded.add(new RequirementEntry(
                            getString(req, "geneId"),
                            getString(req, "displayName"),
                            getString(req, "category"),
                            getString(req, "rarity"),
                            getInt(req, "qualityMin", 1),
                            getInt(req, "qualityMax", 100),
                            getString(req, "description"),
                            getStringList(req, "mobs"),
                            getInt(req, "minQuality", 1),
                            req.has("builderResolved") && req.get("builderResolved").getAsBoolean()
                    ));
                }
                requirements = List.copyOf(decoded);
            }

            return new RecipeEntry(
                    outputId,
                    getString(root, "displayName"),
                    getString(root, "category"),
                    getString(root, "rarity"),
                    getInt(root, "qualityMin", 1),
                    getInt(root, "qualityMax", 100),
                    getString(root, "description"),
                    getStringList(root, "mobs"),
                    getInt(root, "successRate", 100),
                    !root.has("valid") || root.get("valid").getAsBoolean(),
                    getString(root, "invalidReason"),
                    requirements
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String getString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static int getInt(JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static List<String> getStringList(JsonObject object, String key) {
        if (object == null || !object.has(key) || !object.get(key).isJsonArray()) {
            return List.of();
        }
        JsonArray array = object.getAsJsonArray(key);
        ArrayList<String> values = new ArrayList<>();
        for (JsonElement element : array) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            values.add(element.getAsString());
        }
        return List.copyOf(values);
    }

    public record RecipeEntry(
            String outputGeneId,
            String displayName,
            String category,
            String rarity,
            int qualityMin,
            int qualityMax,
            String description,
            List<String> mobs,
            int successRate,
            boolean valid,
            String invalidReason,
            List<RequirementEntry> requirements
    ) {
    }

    public record RequirementEntry(
            String geneId,
            String displayName,
            String category,
            String rarity,
            int qualityMin,
            int qualityMax,
            String description,
            List<String> mobs,
            int minQuality,
            boolean builderResolved
    ) {
    }
}
