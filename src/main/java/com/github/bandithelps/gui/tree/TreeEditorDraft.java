package com.github.bandithelps.gui.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec2;
import net.threetag.palladium.icon.Icon;
import net.threetag.palladium.power.Power;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityDescription;
import net.threetag.palladium.power.ability.AbilityReference;
import net.threetag.palladium.power.ability.unlocking.UnlockingHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TreeEditorDraft {
    public static final float GRID_SNAP = 0.25F;
    private static final float STEP_MIN_SNAP = 0.05F;

    private final Identifier powerId;
    private final String powerName;
    private Identifier backgroundTexture = TreeEditorLayoutBackground.FALLBACK;
    @Nullable
    private JsonObject sourceJson;
    private final List<TreeEditorNode> nodes = new ArrayList<>();

    public TreeEditorDraft(Identifier powerId, String powerName) {
        this.powerId = powerId;
        this.powerName = powerName;
    }

    public static TreeEditorDraft fromPower(Identifier powerId, Power power, @Nullable String sourceJson) {
        TreeEditorDraft draft = new TreeEditorDraft(powerId, power.getName().getString());
        draft.sourceJson = parseSourceJson(sourceJson);
        for (var entry : power.getAbilities().entrySet()) {
            Ability ability = entry.getValue();
            if (ability.getProperties().isHiddenInGUI()) {
                continue;
            }
            Vec2 position = ability.getProperties().getGuiPosition();
            float gridX = position == null ? 0.0F : position.x;
            float gridY = position == null ? 0.0F : position.y;
            String parentKey = firstParentKey(ability);
            if (parentKey == null) {
                parentKey = parentKeyFromSource(draft.sourceJson, entry.getKey());
            }
            Icon icon = ability.getProperties().getIcon();
            String title = ability.getDisplayName().getString();
            DescriptionText description = readDescription(ability);
            draft.nodes.add(new TreeEditorNode(
                    entry.getKey(),
                    title,
                    description.unlocked(),
                    description.locked(),
                    icon,
                    gridX,
                    gridY,
                    parentKey,
                    TreeEditorCostDraft.fromUnlocking(ability.getStateManager().getUnlockingHandler()),
                    TreeConnectionPath.fromProperties(ability.getProperties()),
                    false
            ));
        }
        return draft;
    }

    public Identifier getPowerId() {
        return this.powerId;
    }

    public String getPowerName() {
        return this.powerName;
    }

    public Identifier getBackgroundTexture() {
        return this.backgroundTexture;
    }

    public void setBackgroundTexture(Identifier backgroundTexture) {
        this.backgroundTexture = backgroundTexture == null ? TreeEditorLayoutBackground.FALLBACK : backgroundTexture;
    }

    @Nullable
    public JsonObject getSourceJson() {
        return this.sourceJson;
    }

    public List<TreeEditorNode> getNodes() {
        return this.nodes;
    }

    @Nullable
    public TreeEditorNode find(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        for (TreeEditorNode node : this.nodes) {
            if (node.getKey().equals(key)) {
                return node;
            }
        }
        return null;
    }

    public TreeEditorNode addDummy(float gridX, float gridY) {
        String key = uniqueKey("new_node");
        TreeEditorNode node = TreeEditorNode.created(key, "New Node", gridX, gridY);
        this.nodes.add(node);
        return node;
    }

    public boolean rename(TreeEditorNode node, String newKey) {
        String trimmed = sanitizeKey(newKey);
        if (trimmed.isEmpty()) {
            return false;
        }
        if (!node.isCreated()) {
            return false;
        }
        if (trimmed.equals(node.getKey())) {
            return true;
        }
        if (find(trimmed) != null || hasSourceAbility(trimmed)) {
            return false;
        }
        String oldKey = node.getKey();
        node.setKey(trimmed);
        for (TreeEditorNode other : this.nodes) {
            if (oldKey.equals(other.getParentKey())) {
                other.setParentKey(trimmed);
            }
        }
        return true;
    }

    public boolean setParent(TreeEditorNode child, @Nullable TreeEditorNode parent) {
        if (parent == null) {
            child.setParentKey(null);
            child.setConnectionPath(TreeConnectionPath.EMPTY);
            return true;
        }
        if (child == parent || wouldCycle(child, parent)) {
            return false;
        }
        if (!parent.getKey().equals(child.getParentKey())) {
            child.setConnectionPath(TreeConnectionPath.EMPTY);
        }
        child.setParentKey(parent.getKey());
        return true;
    }

    public boolean remove(TreeEditorNode node) {
        if (!node.isCreated()) {
            return false;
        }
        this.nodes.remove(node);
        for (TreeEditorNode other : this.nodes) {
            if (node.getKey().equals(other.getParentKey())) {
                other.setParentKey(null);
                other.setConnectionPath(TreeConnectionPath.EMPTY);
            }
        }
        return true;
    }

    public List<TreeEditorNode> childrenOf(TreeEditorNode parent) {
        List<TreeEditorNode> children = new ArrayList<>();
        for (TreeEditorNode node : this.nodes) {
            if (parent.getKey().equals(node.getParentKey())) {
                children.add(node);
            }
        }
        return children;
    }

    public static float snap(float value) {
        return snap(value, GRID_SNAP);
    }

    public static float snap(float value, float step) {
        float size = Math.max(STEP_MIN_SNAP, step);
        double snapped = Math.round(value / size) * (double) size;
        return clean(snapped);
    }

    public static float clean(float value) {
        return clean((double) value);
    }

    public static float clean(double value) {
        return (float) (Math.round(value * 100.0) / 100.0);
    }

    public static JsonPrimitive gridNumber(float value) {
        double cleaned = Math.round(value * 100.0) / 100.0;
        long whole = Math.round(cleaned);
        if (Math.abs(cleaned - whole) < 0.001) {
            return new JsonPrimitive(whole);
        }
        return new JsonPrimitive(cleaned);
    }

    public static String keyFromTitle(String title) {
        return sanitizeKey(title);
    }

    private boolean wouldCycle(TreeEditorNode child, TreeEditorNode parent) {
        TreeEditorNode current = parent;
        int guard = 0;
        while (current != null && guard++ < 256) {
            if (current == child) {
                return true;
            }
            current = find(current.getParentKey());
        }
        return false;
    }

    private String uniqueKey(String base) {
        String sanitized = sanitizeKey(base);
        if (find(sanitized) == null && !hasSourceAbility(sanitized)) {
            return sanitized;
        }
        int index = 2;
        while (find(sanitized + "_" + index) != null || hasSourceAbility(sanitized + "_" + index)) {
            index++;
        }
        return sanitized + "_" + index;
    }

    private boolean hasSourceAbility(String key) {
        if (this.sourceJson == null || !this.sourceJson.has("abilities") || !this.sourceJson.get("abilities").isJsonObject()) {
            return false;
        }
        return this.sourceJson.getAsJsonObject("abilities").has(key);
    }

    private static String sanitizeKey(String key) {
        String trimmed = key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        while (trimmed.contains("__")) {
            trimmed = trimmed.replace("__", "_");
        }
        if (trimmed.startsWith("_")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    @Nullable
    private static String firstParentKey(Ability ability) {
        UnlockingHandler handler = ability.getStateManager().getUnlockingHandler();
        List<AbilityReference> parents = handler.getParentAbilities();
        if (parents == null || parents.isEmpty()) {
            return null;
        }
        return localAbilityKey(parents.getFirst().abilityKey());
    }

    @Nullable
    private static String parentKeyFromSource(@Nullable JsonObject sourceJson, String abilityKey) {
        if (sourceJson == null || !sourceJson.has("abilities") || !sourceJson.get("abilities").isJsonObject()) {
            return null;
        }
        JsonObject abilities = sourceJson.getAsJsonObject("abilities");
        if (!abilities.has(abilityKey) || !abilities.get(abilityKey).isJsonObject()) {
            return null;
        }
        JsonObject ability = abilities.getAsJsonObject(abilityKey);
        JsonObject state = ability.has("state") && ability.get("state").isJsonObject()
                ? ability.getAsJsonObject("state")
                : null;
        JsonObject unlocking = state != null && state.has("unlocking") && state.get("unlocking").isJsonObject()
                ? state.getAsJsonObject("unlocking")
                : null;
        if (unlocking == null) {
            return null;
        }
        String direct = readAbilityField(unlocking);
        if (direct != null) {
            return direct;
        }
        String fromRequires = firstAbilityFromListOrObject(unlocking.get("requires"));
        if (fromRequires != null) {
            return fromRequires;
        }
        return firstAbilityFromListOrObject(unlocking.get("conditions"));
    }

    @Nullable
    private static String firstAbilityFromListOrObject(@Nullable JsonElement element) {
        if (element == null) {
            return null;
        }
        if (element.isJsonObject()) {
            return readAbilityField(element.getAsJsonObject());
        }
        if (!element.isJsonArray()) {
            return null;
        }
        for (JsonElement entry : element.getAsJsonArray()) {
            if (entry.isJsonObject()) {
                String nested = readAbilityField(entry.getAsJsonObject());
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    @Nullable
    private static String readAbilityField(JsonObject object) {
        if (!object.has("ability") || !object.get("ability").isJsonPrimitive()) {
            return null;
        }
        return localAbilityKey(object.get("ability").getAsString());
    }

    @Nullable
    private static String localAbilityKey(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        int hash = raw.indexOf('#');
        return hash >= 0 ? raw.substring(hash + 1) : raw;
    }

    private static DescriptionText readDescription(Ability ability) {
        AbilityDescription description = ability.getProperties().getDescription();
        if (description == null) {
            return DescriptionText.EMPTY;
        }
        String unlocked = componentText(description.getUnlockedDescription());
        if (unlocked.isBlank()) {
            unlocked = componentText(description.get(true));
        }
        String locked = description.isSimple() ? "" : componentText(description.getLockedDescription());
        if (locked.isBlank() && !description.isSimple()) {
            locked = componentText(description.get(false));
        }
        if (unlocked.isBlank() && !locked.isBlank()) {
            unlocked = locked;
        }
        return new DescriptionText(unlocked, locked);
    }

    private static String componentText(@Nullable Component component) {
        if (component == null) {
            return "";
        }
        String text = component.getString();
        return text == null ? "" : text;
    }

    @Nullable
    private static JsonObject parseSourceJson(@Nullable String sourceJson) {
        if (sourceJson == null || sourceJson.isBlank()) {
            return null;
        }
        try {
            JsonElement element = JsonParser.parseString(sourceJson);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record DescriptionText(String unlocked, String locked) {
        private static final DescriptionText EMPTY = new DescriptionText("", "");
    }
}
