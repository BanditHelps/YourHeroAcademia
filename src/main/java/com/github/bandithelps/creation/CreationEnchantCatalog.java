package com.github.bandithelps.creation;

import com.github.bandithelps.Config;
import com.github.bandithelps.YourHeroAcademia;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public final class CreationEnchantCatalog {
    private static final CreationEnchantCatalog INSTANCE = new CreationEnchantCatalog();

    private final Map<Identifier, CreationEnchantEntry> entries = new LinkedHashMap<>();

    private CreationEnchantCatalog() {
    }

    public static CreationEnchantCatalog getInstance() {
        return INSTANCE;
    }

    public void reload(ResourceManager resourceManager) {
        entries.clear();
        if (resourceManager == null) {
            return;
        }
        Map<Identifier, Resource> resources = resourceManager.listResources(
                "creation/enchant_knowledge",
                id -> id.getPath().endsWith(".json")
        );
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    throw new JsonParseException("Creation enchant knowledge file must be a json object");
                }
                parseFile(element.getAsJsonObject());
            } catch (Exception e) {
                YourHeroAcademia.LOGGER.warn("Failed to parse creation enchant knowledge {}: {}", entry.getKey(), e.getMessage());
            }
        }
        YourHeroAcademia.LOGGER.info("Registered {} creation enchant catalog entries", entries.size());
    }

    public Optional<CreationEnchantEntry> get(Identifier enchantId) {
        if (enchantId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(enchantId));
    }

    public List<CreationEnchantEntry> allEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    private void parseFile(JsonObject json) {
        String ability = requireString(json, "ability");
        if (ability.contains("#")) {
            ability = ability.substring(ability.indexOf('#') + 1);
        }
        int defaultResearchCost = json.has("research_cost")
                ? clampResearchCost(json.get("research_cost").getAsInt())
                : Config.CREATION_RESEARCH_SACRIFICES.get();
        if (!json.has("entries") || !json.get("entries").isJsonArray()) {
            throw new JsonParseException("Missing entries array");
        }
        for (JsonElement element : json.getAsJsonArray("entries")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("enchant")) {
                continue;
            }
            Identifier enchantId = Identifier.parse(entry.get("enchant").getAsString());
            int researchCost = entry.has("research_cost")
                    ? clampResearchCost(entry.get("research_cost").getAsInt())
                    : defaultResearchCost;
            int[] lipidCosts = parseLipidCosts(entry);
            int perLevel = entry.has("lipid_cost_per_level")
                    ? Math.max(1, entry.get("lipid_cost_per_level").getAsInt())
                    : Config.CREATION_DEFAULT_LIPID_COST.get();
            Integer maxLevel = entry.has("max_level") ? Math.max(1, entry.get("max_level").getAsInt()) : null;
            entries.putIfAbsent(enchantId, new CreationEnchantEntry(
                    enchantId, ability, perLevel, lipidCosts, researchCost, maxLevel));
        }
    }

    private static int[] parseLipidCosts(JsonObject entry) {
        if (!entry.has("lipid_costs") || !entry.get("lipid_costs").isJsonArray()) {
            return null;
        }
        JsonArray array = entry.getAsJsonArray("lipid_costs");
        if (array.isEmpty()) {
            return null;
        }
        int[] costs = new int[array.size()];
        for (int i = 0; i < array.size(); i++) {
            costs[i] = Math.max(1, array.get(i).getAsInt());
        }
        return costs;
    }

    private static int clampResearchCost(int cost) {
        return Math.max(1, Math.min(64, cost));
    }

    private static String requireString(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new JsonParseException("Missing required key: " + key);
        }
        return json.get(key).getAsString();
    }
}
