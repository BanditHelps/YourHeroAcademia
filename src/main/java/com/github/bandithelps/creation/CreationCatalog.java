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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class CreationCatalog {
    private static final CreationCatalog INSTANCE = new CreationCatalog();

    private final Map<Identifier, CreationEntry> entriesByItem = new LinkedHashMap<>();
    private final List<RawSpec> rawSpecs = new ArrayList<>();

    private CreationCatalog() {
    }

    public static CreationCatalog getInstance() {
        return INSTANCE;
    }

    public void reload(ResourceManager resourceManager) {
        rawSpecs.clear();
        entriesByItem.clear();
        if (resourceManager == null) {
            return;
        }
        Map<Identifier, Resource> resources = resourceManager.listResources(
                "creation/knowledge",
                id -> id.getPath().endsWith(".json")
        );
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            try (var reader = entry.getValue().openAsReader()) {
                JsonElement element = JsonParser.parseReader(reader);
                if (!element.isJsonObject()) {
                    throw new JsonParseException("Creation knowledge file must be a json object");
                }
                parseFile(element.getAsJsonObject());
            } catch (Exception e) {
                YourHeroAcademia.LOGGER.warn("Failed to parse creation knowledge {}: {}", entry.getKey(), e.getMessage());
            }
        }
        rebuildResolved();
        YourHeroAcademia.LOGGER.info("Registered {} creation catalog items", entriesByItem.size());
    }

    public void rebuildResolved() {
        entriesByItem.clear();
        for (RawSpec spec : rawSpecs) {
            for (Identifier itemId : resolveSpec(spec)) {
                if (stackOf(itemId).isEmpty()) {
                    continue;
                }
                entriesByItem.putIfAbsent(itemId, new CreationEntry(itemId, spec.tab(), spec.abilityKey(), spec.lipidCost()));
            }
        }
    }

    public Optional<CreationEntry> get(Identifier itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        CreationEntry entry = entriesByItem.get(itemId);
        if (entry != null) {
            return Optional.of(entry);
        }
        rebuildResolved();
        return Optional.ofNullable(entriesByItem.get(itemId));
    }

    public List<CreationEntry> allEntries() {
        if (entriesByItem.isEmpty() && !rawSpecs.isEmpty()) {
            rebuildResolved();
        }
        return Collections.unmodifiableList(new ArrayList<>(entriesByItem.values()));
    }

    public static ItemStack stackOf(Identifier itemId) {
        if (itemId == null) {
            return ItemStack.EMPTY;
        }
        return BuiltInRegistries.ITEM.get(itemId)
                .map(holder -> new ItemStack(holder.value()))
                .orElse(ItemStack.EMPTY);
    }

    public static Identifier idOf(Item item) {
        if (item == null || item == Items.AIR) {
            return null;
        }
        return BuiltInRegistries.ITEM.getKey(item);
    }

    private void parseFile(JsonObject json) {
        String ability = requireString(json, "ability");
        if (ability.contains("#")) {
            ability = ability.substring(ability.indexOf('#') + 1);
        }
        CreationTab defaultTab = CreationTab.fromId(json.has("tab") ? json.get("tab").getAsString() : "materials");
        if (!json.has("entries") || !json.get("entries").isJsonArray()) {
            throw new JsonParseException("Missing entries array");
        }
        JsonArray entries = json.getAsJsonArray("entries");
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            CreationTab tab = entry.has("tab") ? CreationTab.fromId(entry.get("tab").getAsString()) : defaultTab;
            int cost = entry.has("lipid_cost")
                    ? Math.max(1, entry.get("lipid_cost").getAsInt())
                    : Config.CREATION_DEFAULT_LIPID_COST.get();
            if (entry.has("item")) {
                Identifier itemId = Identifier.parse(entry.get("item").getAsString());
                rawSpecs.add(new RawSpec(ability, tab, cost, itemId, null));
            } else if (entry.has("tag")) {
                Identifier tagId = Identifier.parse(entry.get("tag").getAsString());
                rawSpecs.add(new RawSpec(ability, tab, cost, null, tagId));
            }
        }
    }

    private static List<Identifier> resolveSpec(RawSpec spec) {
        List<Identifier> ids = new ArrayList<>();
        if (spec.itemId() != null) {
            ids.add(spec.itemId());
            return ids;
        }
        if (spec.tagId() == null) {
            return ids;
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, spec.tagId());
        for (Item item : BuiltInRegistries.ITEM) {
            if (!item.builtInRegistryHolder().is(tag)) {
                continue;
            }
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    private static String requireString(JsonObject json, String key) {
        if (!json.has(key)) {
            throw new JsonParseException("Missing required key: " + key);
        }
        return json.get(key).getAsString();
    }

    private record RawSpec(String abilityKey, CreationTab tab, int lipidCost, Identifier itemId, Identifier tagId) {
    }
}
