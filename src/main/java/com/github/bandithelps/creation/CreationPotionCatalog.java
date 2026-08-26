package com.github.bandithelps.creation;

import com.github.bandithelps.Config;
import com.github.bandithelps.YourHeroAcademia;
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

public final class CreationPotionCatalog {
    private static final CreationPotionCatalog INSTANCE = new CreationPotionCatalog();

    private final Map<Identifier, CreationPotionEntry> entries = new LinkedHashMap<>();
    private final Map<Identifier, PotionGroup> groups = new LinkedHashMap<>();

    private CreationPotionCatalog() {
    }

    public static CreationPotionCatalog getInstance() {
        return INSTANCE;
    }

    public void reload(ResourceManager resourceManager) {
        entries.clear();
        groups.clear();
        if (resourceManager == null) {
            return;
        }
        Map<Identifier, Resource> resources = resourceManager.listResources(
                "creation/potion_knowledge",
                id -> id.getPath().endsWith(".json")
        );
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    throw new JsonParseException("Creation potion knowledge file must be a json object");
                }
                parseFile(element.getAsJsonObject());
            } catch (Exception e) {
                YourHeroAcademia.LOGGER.warn("Failed to parse creation potion knowledge {}: {}", entry.getKey(), e.getMessage());
            }
        }
        YourHeroAcademia.LOGGER.info("Registered {} creation potion catalog entries in {} groups", entries.size(), groups.size());
    }

    public Optional<CreationPotionEntry> get(Identifier effectId) {
        if (effectId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(entries.get(effectId));
    }

    public List<CreationPotionEntry> allEntries() {
        return Collections.unmodifiableList(new ArrayList<>(entries.values()));
    }

    public List<PotionGroup> groups() {
        return Collections.unmodifiableList(new ArrayList<>(groups.values()));
    }

    public Optional<PotionGroup> group(Identifier groupId) {
        if (groupId == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(groups.get(groupId));
    }

    private void parseFile(JsonObject json) {
        String ability = requireString(json, "ability");
        if (ability.contains("#")) {
            ability = ability.substring(ability.indexOf('#') + 1);
        }
        int defaultResearchCost = json.has("research_cost")
                ? clampResearchCost(json.get("research_cost").getAsInt())
                : Config.CREATION_RESEARCH_SACRIFICES.get();
        Identifier fileGroupId = optionalId(json, "group");
        Identifier fileGroupIcon = optionalId(json, "group_icon");
        if (!json.has("entries") || !json.get("entries").isJsonArray()) {
            throw new JsonParseException("Missing entries array");
        }
        for (JsonElement element : json.getAsJsonArray("entries")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            if (!entry.has("effect")) {
                continue;
            }
            Identifier effectId = Identifier.parse(entry.get("effect").getAsString());
            String entryAbility = entry.has("ability") ? entry.get("ability").getAsString() : ability;
            if (entryAbility.contains("#")) {
                entryAbility = entryAbility.substring(entryAbility.indexOf('#') + 1);
            }
            int researchCost = entry.has("research_cost")
                    ? clampResearchCost(entry.get("research_cost").getAsInt())
                    : defaultResearchCost;
            int lipidCost = entry.has("lipid_cost")
                    ? Math.max(1, entry.get("lipid_cost").getAsInt())
                    : Config.CREATION_DEFAULT_LIPID_COST.get();
            int perAmplifier = entry.has("lipid_cost_per_amplifier")
                    ? Math.max(0, entry.get("lipid_cost_per_amplifier").getAsInt())
                    : lipidCost;
            int maxDuration = entry.has("max_duration_seconds")
                    ? Math.max(1, entry.get("max_duration_seconds").getAsInt())
                    : CreationPotionEntry.DEFAULT_MAX_DURATION_SECONDS;
            Boolean instant = entry.has("instant") ? entry.get("instant").getAsBoolean() : null;
            Float chance = entry.has("experiential_chance")
                    ? (float) Math.max(0.0, Math.min(1.0, entry.get("experiential_chance").getAsDouble()))
                    : null;
            Identifier groupId = entry.has("group") ? optionalId(entry, "group") : fileGroupId;
            Identifier groupIcon = entry.has("group_icon") ? optionalId(entry, "group_icon") : fileGroupIcon;
            if (groupId == null) {
                groupId = effectId;
            }
            if (groupIcon == null) {
                groupIcon = Identifier.fromNamespaceAndPath("minecraft", "potion");
            }
            if (entries.containsKey(effectId)) {
                continue;
            }
            CreationPotionEntry parsed = new CreationPotionEntry(
                    effectId,
                    entryAbility,
                    groupId,
                    groupIcon,
                    lipidCost,
                    perAmplifier,
                    researchCost,
                    maxDuration,
                    instant,
                    chance
            );
            entries.put(effectId, parsed);
            PotionGroup group = groups.get(groupId);
            if (group == null) {
                group = new PotionGroup(groupId, groupIcon, new ArrayList<>());
                groups.put(groupId, group);
            }
            group.effectIds().add(effectId);
        }
    }

    private static Identifier optionalId(JsonObject json, String key) {
        if (!json.has(key) || json.get(key).isJsonNull()) {
            return null;
        }
        String raw = json.get(key).getAsString();
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Identifier.parse(raw);
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

    public record PotionGroup(Identifier id, Identifier icon, List<Identifier> effectIds) {
        public boolean isSingleton() {
            return this.effectIds.size() <= 1;
        }
    }
}
