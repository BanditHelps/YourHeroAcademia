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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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
    private final Map<Identifier, Identifier> formToParent = new LinkedHashMap<>();
    private final List<RawSpec> rawSpecs = new ArrayList<>();

    private CreationCatalog() {
    }

    public static CreationCatalog getInstance() {
        return INSTANCE;
    }

    public void reload(ResourceManager resourceManager) {
        rawSpecs.clear();
        entriesByItem.clear();
        formToParent.clear();
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
        formToParent.clear();
        List<ResolvedItem> pending = new ArrayList<>();
        for (RawSpec spec : rawSpecs) {
            for (Identifier itemId : resolveIds(spec.itemId(), spec.tagId())) {
                if (stackOf(itemId).isEmpty()) {
                    continue;
                }
                pending.add(new ResolvedItem(itemId, spec));
            }
        }
        Set<Identifier> catalogIds = new LinkedHashSet<>();
        for (ResolvedItem resolved : pending) {
            catalogIds.add(resolved.itemId());
        }
        for (ResolvedItem resolved : pending) {
            Identifier itemId = resolved.itemId();
            if (entriesByItem.containsKey(itemId)) {
                continue;
            }
            RawSpec spec = resolved.spec();
            Identifier nuggetId = usable(spec.nuggetId());
            Identifier blockId = usable(spec.blockId());
            Identifier groupId = spec.groupId() != null ? spec.groupId() : itemId;
            Identifier groupIcon = spec.groupIcon() != null ? spec.groupIcon() : itemId;
            List<Identifier> unlockVariants = resolveUnlockVariants(spec, itemId, catalogIds);
            entriesByItem.put(itemId, new CreationEntry(
                    itemId,
                    spec.tab(),
                    spec.abilityKey(),
                    spec.lipidCost(),
                    spec.researchCost(),
                    nuggetId,
                    blockId,
                    groupId,
                    groupIcon,
                    spec.unlockAbility(),
                    spec.unlockMode(),
                    unlockVariants
            ));
            registerForm(nuggetId, itemId);
            registerForm(blockId, itemId);
            for (Identifier variantId : unlockVariants) {
                registerForm(variantId, itemId);
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

    public Optional<CreationEntry> parentOf(Identifier itemId) {
        if (itemId == null) {
            return Optional.empty();
        }
        if (entriesByItem.isEmpty() && !rawSpecs.isEmpty()) {
            rebuildResolved();
        }
        CreationEntry direct = entriesByItem.get(itemId);
        if (direct != null) {
            return Optional.of(direct);
        }
        Identifier parentId = formToParent.get(itemId);
        if (parentId == null) {
            rebuildResolved();
            direct = entriesByItem.get(itemId);
            if (direct != null) {
                return Optional.of(direct);
            }
            parentId = formToParent.get(itemId);
        }
        return parentId == null ? Optional.empty() : Optional.ofNullable(entriesByItem.get(parentId));
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
        String ability = stripAbility(requireString(json, "ability"));
        CreationTab defaultTab = CreationTab.fromId(json.has("tab") ? json.get("tab").getAsString() : "materials");
        int defaultResearchCost = json.has("research_cost")
                ? clampResearchCost(json.get("research_cost").getAsInt())
                : Config.CREATION_RESEARCH_SACRIFICES.get();
        Identifier fileGroupId = optionalId(json, "group");
        Identifier fileGroupIcon = optionalId(json, "group_icon");
        if (!json.has("entries") || !json.get("entries").isJsonArray()) {
            throw new JsonParseException("Missing entries array");
        }
        JsonArray entries = json.getAsJsonArray("entries");
        for (JsonElement element : entries) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            String entryAbility = entry.has("ability") ? stripAbility(entry.get("ability").getAsString()) : ability;
            CreationTab tab = entry.has("tab") ? CreationTab.fromId(entry.get("tab").getAsString()) : defaultTab;
            int cost = entry.has("lipid_cost")
                    ? Math.max(1, entry.get("lipid_cost").getAsInt())
                    : Config.CREATION_DEFAULT_LIPID_COST.get();
            int researchCost = entry.has("research_cost")
                    ? clampResearchCost(entry.get("research_cost").getAsInt())
                    : defaultResearchCost;
            Identifier nuggetId = optionalId(entry, "nugget");
            Identifier blockId = optionalId(entry, "block");
            Identifier groupId = entry.has("group") ? optionalId(entry, "group") : fileGroupId;
            Identifier groupIcon = entry.has("group_icon") ? optionalId(entry, "group_icon") : fileGroupIcon;
            Identifier unlockTag = optionalId(entry, "unlock_tag");
            List<Identifier> unlockItems = optionalIdList(entry, "unlock_items");
            CreationUnlockMode unlockMode = entry.has("unlock_mode")
                    ? CreationUnlockMode.fromId(entry.get("unlock_mode").getAsString())
                    : CreationUnlockMode.ABILITY;
            String unlockAbility = null;
            if (unlockTag != null || !unlockItems.isEmpty()) {
                if (unlockMode == CreationUnlockMode.WOOD) {
                    unlockAbility = entry.has("unlock_ability")
                            ? stripAbility(entry.get("unlock_ability").getAsString())
                            : null;
                } else {
                    unlockAbility = entry.has("unlock_ability")
                            ? stripAbility(entry.get("unlock_ability").getAsString())
                            : CreationUtil.DYE_KNOWLEDGE;
                }
            }
            if (entry.has("item")) {
                Identifier itemId = Identifier.parse(entry.get("item").getAsString());
                rawSpecs.add(new RawSpec(
                        entryAbility, tab, cost, researchCost, itemId, null, nuggetId, blockId,
                        groupId, groupIcon, unlockAbility, unlockMode, unlockTag, unlockItems
                ));
            } else if (entry.has("tag")) {
                Identifier tagId = Identifier.parse(entry.get("tag").getAsString());
                rawSpecs.add(new RawSpec(
                        entryAbility, tab, cost, researchCost, null, tagId, null, null,
                        groupId, groupIcon, unlockAbility, unlockMode, unlockTag, unlockItems
                ));
            }
        }
    }

    private static List<Identifier> resolveUnlockVariants(RawSpec spec, Identifier baseId, Set<Identifier> catalogIds) {
        List<Identifier> ids = new ArrayList<>();
        Set<Identifier> seen = new LinkedHashSet<>();
        for (Identifier itemId : spec.unlockItems()) {
            addUnlockVariant(ids, seen, itemId, baseId, catalogIds);
        }
        if (spec.unlockTag() != null) {
            for (Identifier itemId : resolveIds(null, spec.unlockTag())) {
                addUnlockVariant(ids, seen, itemId, baseId, catalogIds);
            }
        }
        return ids;
    }

    private static void addUnlockVariant(
            List<Identifier> ids,
            Set<Identifier> seen,
            Identifier itemId,
            Identifier baseId,
            Set<Identifier> catalogIds
    ) {
        Identifier usableId = usable(itemId);
        if (usableId == null || usableId.equals(baseId) || catalogIds.contains(usableId) || !seen.add(usableId)) {
            return;
        }
        ids.add(usableId);
    }

    private static List<Identifier> resolveIds(Identifier itemId, Identifier tagId) {
        List<Identifier> ids = new ArrayList<>();
        if (itemId != null) {
            ids.add(itemId);
            return ids;
        }
        if (tagId == null) {
            return ids;
        }
        TagKey<Item> tag = TagKey.create(Registries.ITEM, tagId);
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

    private void registerForm(Identifier formId, Identifier parentId) {
        if (formId == null || parentId == null || formId.equals(parentId)) {
            return;
        }
        formToParent.putIfAbsent(formId, parentId);
    }

    private static Identifier usable(Identifier itemId) {
        if (itemId == null || stackOf(itemId).isEmpty()) {
            return null;
        }
        return itemId;
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

    private static List<Identifier> optionalIdList(JsonObject json, String key) {
        if (!json.has(key) || !json.get(key).isJsonArray()) {
            return List.of();
        }
        List<Identifier> ids = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray(key)) {
            if (!element.isJsonPrimitive()) {
                continue;
            }
            String raw = element.getAsString();
            if (raw == null || raw.isBlank()) {
                continue;
            }
            ids.add(Identifier.parse(raw));
        }
        return ids;
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

    private static String stripAbility(String ability) {
        if (ability == null) {
            return null;
        }
        if (ability.contains("#")) {
            return ability.substring(ability.indexOf('#') + 1);
        }
        return ability;
    }

    private record ResolvedItem(Identifier itemId, RawSpec spec) {
    }

    private record RawSpec(
            String abilityKey,
            CreationTab tab,
            int lipidCost,
            int researchCost,
            Identifier itemId,
            Identifier tagId,
            Identifier nuggetId,
            Identifier blockId,
            Identifier groupId,
            Identifier groupIcon,
            String unlockAbility,
            CreationUnlockMode unlockMode,
            Identifier unlockTag,
            List<Identifier> unlockItems
    ) {
        RawSpec {
            unlockItems = unlockItems == null ? List.of() : List.copyOf(unlockItems);
            unlockMode = unlockMode == null ? CreationUnlockMode.ABILITY : unlockMode;
        }
    }
}
