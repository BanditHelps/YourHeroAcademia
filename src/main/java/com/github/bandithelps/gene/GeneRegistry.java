package com.github.bandithelps.gene;

import com.github.bandithelps.YourHeroAcademia;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class GeneRegistry {
    private static final GeneRegistry INSTANCE = new GeneRegistry();

    private final Map<String, GeneType> geneTypesById = new HashMap<>();
    private final Map<GeneCategory, List<GeneType>> geneTypesByCategory = new HashMap<>();

    private GeneRegistry() {
    }

    public static GeneRegistry getInstance() {
        return INSTANCE;
    }

    public void register(GeneType geneType) {
        if (geneType == null) {
            throw new IllegalArgumentException("Gene type cannot be null");
        }

        String id = geneType.getId().toLowerCase(Locale.ROOT);
        this.geneTypesById.put(id, geneType);
        this.geneTypesByCategory.computeIfAbsent(geneType.getCategory(), k -> new ArrayList<>()).add(geneType);
    }

    public Optional<GeneType> getGeneType(String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(this.geneTypesById.get(id.toLowerCase(Locale.ROOT)));
    }

    public List<GeneType> getGeneTypesByCategory(GeneCategory category) {
        return Collections.unmodifiableList(this.geneTypesByCategory.getOrDefault(category, Collections.emptyList()));
    }

    public List<GeneType> getAllGeneTypes() {
        return Collections.unmodifiableList(new ArrayList<>(this.geneTypesById.values()));
    }

    public int reload(ResourceManager resourceManager) {
        clear();
        if (resourceManager == null) {
            return 0;
        }

        Map<Identifier, Resource> resources = resourceManager.listResources("genes", id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier location = entry.getKey();
            try (var reader = entry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    throw new JsonParseException("Gene definition must be a json object");
                }
                GeneType geneType = parseGeneType(location, element.getAsJsonObject());
                register(geneType);
            } catch (Exception e) {
                YourHeroAcademia.LOGGER.warn("Failed to parse gene data {}: {}", location, e.getMessage());
            }
        }

        for (Map.Entry<GeneCategory, List<GeneType>> entry : this.geneTypesByCategory.entrySet()) {
            entry.getValue().sort((a, b) -> a.getId().compareToIgnoreCase(b.getId()));
        }

        YourHeroAcademia.LOGGER.info("Registered {} genes", this.geneTypesById.size());
        return this.geneTypesById.size();
    }

    private static GeneType parseGeneType(Identifier location, JsonObject json) {
        String id = requireString(json, "id");
        Identifier.parse(id);

        String categoryRaw = requireString(json, "category");
        GeneCategory category = GeneCategory.valueOf(categoryRaw.toUpperCase(Locale.ROOT));

        String rarityRaw = requireString(json, "rarity");
        GeneRarity rarity = GeneRarity.valueOf(rarityRaw.toUpperCase(Locale.ROOT));

        JsonArray qualityArray = requireArray(json, "qualityRange");
        if (qualityArray.size() != 2) {
            throw new JsonParseException("qualityRange must contain exactly 2 numbers");
        }
        int qualityMin = qualityArray.get(0).getAsInt();
        int qualityMax = qualityArray.get(1).getAsInt();
        if (qualityMin > qualityMax) {
            throw new JsonParseException("qualityRange min cannot be greater than max");
        }

        boolean combinable = json.has("combinable") && json.get("combinable").getAsBoolean();
        String description = json.has("description") ? json.get("description").getAsString() : "";
        List<String> mobs = parseMobs(json);
        List<GeneType.AttributeEffect> attributeEffects = category == GeneCategory.ATTRIBUTE
                ? parseAttributeEffects(json)
                : Collections.emptyList();

        GeneType.CombinationRecipe recipe = null;
        if (json.has("combination") && json.get("combination").isJsonObject()) {
            recipe = parseCombination(json.getAsJsonObject("combination"));
        } else if (combinable) {
            throw new JsonParseException("combinable genes must define a combination recipe");
        }

        return new GeneType(id, category, rarity, description, qualityMin, qualityMax, combinable, recipe, mobs, attributeEffects);
    }

    private static List<GeneType.AttributeEffect> parseAttributeEffects(JsonObject json) {
        List<GeneType.AttributeEffect> effects = new ArrayList<>();
        if (json.has("attributes") && json.get("attributes").isJsonArray()) {
            JsonArray attributesArray = json.getAsJsonArray("attributes");
            for (JsonElement element : attributesArray) {
                if (!element.isJsonObject()) {
                    continue;
                }
                effects.add(parseAttributeEffectObject(element.getAsJsonObject()));
            }
            if (effects.isEmpty()) {
                throw new JsonParseException("attributes must include at least one valid object entry");
            }
            return effects;
        }

        // Legacy fallback for older datapacks that still use singular fields.
        if (json.has("attribute")) {
            effects.add(parseAttributeEffectObject(json));
            return effects;
        }

        throw new JsonParseException("Attribute genes must define an attributes array");
    }

    private static GeneType.AttributeEffect parseAttributeEffectObject(JsonObject json) {
        String attributeId = json.has("id") ? json.get("id").getAsString() : requireString(json, "attribute");
        Identifier.parse(attributeId);
        if (!json.has("minModifier")) {
            throw new JsonParseException("Missing required key: minModifier");
        }
        if (!json.has("maxModifier")) {
            throw new JsonParseException("Missing required key: maxModifier");
        }
        double minModifier = json.get("minModifier").getAsDouble();
        double maxModifier = json.get("maxModifier").getAsDouble();
        return new GeneType.AttributeEffect(attributeId, minModifier, maxModifier);
    }

    private static GeneType.CombinationRecipe parseCombination(JsonObject combinationJson) {
        List<GeneType.Requirement> requirements = new ArrayList<>();
        if (combinationJson.has("requires") && combinationJson.get("requires").isJsonArray()) {
            for (JsonElement requirementElement : combinationJson.getAsJsonArray("requires")) {
                if (!requirementElement.isJsonObject()) {
                    continue;
                }
                JsonObject requirementObject = requirementElement.getAsJsonObject();
                String requirementId = requireString(requirementObject, "id");
                Identifier.parse(requirementId);
                int minQuality = requirementObject.has("minQuality") ? requirementObject.get("minQuality").getAsInt() : 1;
                requirements.add(new GeneType.Requirement(requirementId, minQuality));
            }
        }

        JsonObject builderObject = combinationJson.has("builder") && combinationJson.get("builder").isJsonObject()
                ? combinationJson.getAsJsonObject("builder")
                : null;
        int builderCount = builderObject != null && builderObject.has("count") ? builderObject.get("count").getAsInt() : 0;
        int builderMinQuality = builderObject != null && builderObject.has("minQuality") ? builderObject.get("minQuality").getAsInt() : 1;
        int successRate = combinationJson.has("successRate") ? combinationJson.get("successRate").getAsInt() : 100;

        return new GeneType.CombinationRecipe(requirements, builderCount, builderMinQuality, successRate);
    }

    private static String requireString(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new JsonParseException("Missing required key: " + key);
        }
        return json.get(key).getAsString();
    }

    private static List<String> parseMobs(JsonObject json) {
        List<String> mobs = new ArrayList<>();
        if (!json.has("mobs") || !json.get("mobs").isJsonArray()) {
            return mobs;
        }

        for (JsonElement element : json.getAsJsonArray("mobs")) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String mobId = element.getAsString();
            Identifier.parse(mobId);
            mobs.add(mobId.toLowerCase(Locale.ROOT));
        }
        return mobs;
    }

    private static JsonArray requireArray(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            throw new JsonParseException("Missing required array key: " + key);
        }
        return json.getAsJsonArray(key);
    }

    public void clear() {
        this.geneTypesById.clear();
        this.geneTypesByCategory.clear();
    }
}