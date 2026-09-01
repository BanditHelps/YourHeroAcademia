package com.github.bandithelps.gui.tree;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.resources.Identifier;
import net.threetag.palladium.power.Power;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TreeEditorDraft {
    public static final float GRID_SNAP = 0.25F;
    public static final Identifier NEW_POWER_ID = Identifier.fromNamespaceAndPath("yha", "new_power");
    private static final float STEP_MIN_SNAP = 0.05F;

    private Identifier powerId;
    private String powerName;
    private String powerIcon = "minecraft:paper";
    @Nullable
    private String parentPower;
    private String guiDisplayType = "tree";
    private String exportFileName;
    private boolean saveNamed;
    private Identifier backgroundTexture = TreeEditorLayoutBackground.FALLBACK;
    private JsonObject extraRoot = new JsonObject();
    private final List<TreeEditorNode> nodes = new ArrayList<>();
    private boolean dirty;

    public TreeEditorDraft(Identifier powerId, String powerName) {
        this.powerId = powerId;
        this.powerName = powerName;
        this.exportFileName = powerId.getPath();
    }

    public static TreeEditorDraft blank() {
        return blank(NEW_POWER_ID, "New Power");
    }

    public static TreeEditorDraft blank(Identifier powerId, String powerName) {
        TreeEditorDraft draft = new TreeEditorDraft(powerId, powerName);
        draft.powerIcon = "minecraft:paper";
        draft.guiDisplayType = "tree";
        draft.dirty = false;
        return draft;
    }

    public static TreeEditorDraft fromPower(Identifier powerId, Power power, @Nullable String sourceJson) {
        JsonObject json = parseSourceJson(sourceJson);
        if (json != null) {
            return fromJson(powerId, json);
        }
        TreeEditorDraft draft = new TreeEditorDraft(powerId, power.getName().getString());
        draft.saveNamed = true;
        draft.dirty = false;
        return draft;
    }

    public static TreeEditorDraft fromJson(Identifier powerId, JsonObject root) {
        String name = root.has("name") && root.get("name").isJsonPrimitive()
                ? root.get("name").getAsString()
                : powerId.getPath();
        TreeEditorDraft draft = new TreeEditorDraft(powerId, name);
        draft.powerIcon = root.has("icon") && root.get("icon").isJsonPrimitive()
                ? root.get("icon").getAsString()
                : "minecraft:paper";
        draft.parentPower = root.has("parent") && root.get("parent").isJsonPrimitive()
                ? root.get("parent").getAsString()
                : null;
        draft.guiDisplayType = root.has("gui_display_type") && root.get("gui_display_type").isJsonPrimitive()
                ? root.get("gui_display_type").getAsString()
                : "tree";
        JsonObject extra = root.deepCopy();
        extra.remove("name");
        extra.remove("icon");
        extra.remove("parent");
        extra.remove("gui_display_type");
        extra.remove("abilities");
        draft.extraRoot = extra;
        if (root.has("abilities") && root.get("abilities").isJsonObject()) {
            for (var entry : root.getAsJsonObject("abilities").entrySet()) {
                if (entry.getValue().isJsonObject()) {
                    draft.nodes.add(TreeEditorNode.fromAbilityJson(entry.getKey(), entry.getValue().getAsJsonObject()));
                }
            }
        }
        draft.saveNamed = true;
        draft.dirty = false;
        return draft;
    }

    public Identifier getPowerId() {
        return this.powerId;
    }

    public void setPowerId(Identifier powerId) {
        this.powerId = powerId;
        this.markDirty();
    }

    public String getPowerName() {
        return this.powerName;
    }

    public void setPowerName(String powerName) {
        this.powerName = powerName == null ? "" : powerName;
        this.markDirty();
    }

    public String getPowerIcon() {
        return this.powerIcon;
    }

    public void setPowerIcon(String powerIcon) {
        this.powerIcon = powerIcon == null || powerIcon.isBlank() ? "minecraft:paper" : powerIcon;
        this.markDirty();
    }

    @Nullable
    public String getParentPower() {
        return this.parentPower;
    }

    public void setParentPower(@Nullable String parentPower) {
        this.parentPower = parentPower == null || parentPower.isBlank() ? null : parentPower;
        this.markDirty();
    }

    public String getGuiDisplayType() {
        return this.guiDisplayType;
    }

    public void setGuiDisplayType(String guiDisplayType) {
        this.guiDisplayType = guiDisplayType == null || guiDisplayType.isBlank() ? "tree" : guiDisplayType;
        this.markDirty();
    }

    public String getExportFileName() {
        return this.exportFileName;
    }

    public boolean hasSaveName() {
        return this.saveNamed && this.exportFileName != null && !this.exportFileName.isBlank();
    }

    public void markSaveNamed() {
        this.saveNamed = true;
    }

    public void setExportFileName(String exportFileName) {
        this.exportFileName = sanitizeFileName(exportFileName);
        this.markDirty();
    }

    public Identifier getBackgroundTexture() {
        return this.backgroundTexture;
    }

    public void setBackgroundTexture(Identifier backgroundTexture) {
        this.backgroundTexture = backgroundTexture == null ? TreeEditorLayoutBackground.FALLBACK : backgroundTexture;
    }

    public JsonObject getExtraRoot() {
        return this.extraRoot;
    }

    public List<TreeEditorNode> getNodes() {
        return this.nodes;
    }

    public boolean isDirty() {
        return this.dirty;
    }

    public void markDirty() {
        this.dirty = true;
    }

    public void markClean() {
        this.dirty = false;
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
        TreeEditorNode node = this.addAbility(TreeEditorNode.DEFAULT_TYPE, gridX, gridY);
        node.setHiddenInBar(true);
        return node;
    }

    public TreeEditorNode addAbility(String typeId, float gridX, float gridY) {
        String key = uniqueKey("new_node");
        TreeEditorNode node = TreeEditorNode.created(key, typeId, "New Node", gridX, gridY);
        this.nodes.add(node);
        this.markDirty();
        return node;
    }

    public TreeEditorNode duplicate(TreeEditorNode node) {
        String key = uniqueKey(node.getKey());
        float gridX = snap(node.getGridX() + 1.0F);
        float gridY = snap(node.getGridY() + 1.0F);
        TreeEditorNode copy = node.copy(key, gridX, gridY);
        this.nodes.add(copy);
        this.markDirty();
        return copy;
    }

    public boolean rename(TreeEditorNode node, String newKey) {
        String trimmed = sanitizeKey(newKey);
        if (trimmed.isEmpty()) {
            return false;
        }
        if (trimmed.equals(node.getKey())) {
            return true;
        }
        if (find(trimmed) != null) {
            return false;
        }
        String oldKey = node.getKey();
        node.setKey(trimmed);
        for (TreeEditorNode other : this.nodes) {
            if (other.hasParent(oldKey)) {
                other.replaceParentKey(oldKey, trimmed);
            }
            other.setConnectionPaths(other.getConnectionPaths().replaceParentKey(oldKey, trimmed));
            TreeEditorStateSync.replaceAbilityRefs(other.getUnlocking(), oldKey, trimmed);
            TreeEditorStateSync.replaceAbilityRefs(other.getEnabling(), oldKey, trimmed);
            TreeEditorStateSync.replaceAbilityRefs(other.getTypeFields(), oldKey, trimmed);
            other.setParentKeys(TreeEditorStateSync.parentKeysFromUnlocking(other.getUnlocking()));
        }
        this.markDirty();
        return true;
    }

    public boolean setParent(TreeEditorNode child, @Nullable TreeEditorNode parent, List<TreeEditorCostSchema> schemas) {
        if (parent == null) {
            return this.clearParents(child, schemas);
        }
        return this.addParent(child, parent, schemas);
    }

    public boolean addParent(TreeEditorNode child, TreeEditorNode parent, List<TreeEditorCostSchema> schemas) {
        if (child == parent || child.hasParent(parent.getKey()) || wouldCycle(child, parent)) {
            return false;
        }
        TreeEditorStateSync.addParent(child, parent.getKey(), schemas);
        this.markDirty();
        return true;
    }

    public boolean clearParents(TreeEditorNode child, List<TreeEditorCostSchema> schemas) {
        TreeEditorStateSync.removeAllParents(child);
        TreeEditorStateSync.applyCost(child, schemas);
        child.clearConnectionPaths();
        this.markDirty();
        return true;
    }

    public boolean removeParent(TreeEditorNode child, String parentKey, List<TreeEditorCostSchema> schemas) {
        TreeEditorStateSync.removeParent(child, parentKey);
        child.removeConnectionPath(parentKey);
        this.markDirty();
        return true;
    }

    public boolean remove(TreeEditorNode node) {
        this.nodes.remove(node);
        for (TreeEditorNode other : this.nodes) {
            if (other.hasParent(node.getKey())) {
                TreeEditorStateSync.removeParent(other, node.getKey());
                other.removeConnectionPath(node.getKey());
            }
        }
        this.markDirty();
        return true;
    }

    public List<TreeEditorNode> childrenOf(TreeEditorNode parent) {
        List<TreeEditorNode> children = new ArrayList<>();
        for (TreeEditorNode node : this.nodes) {
            if (node.hasParent(parent.getKey())) {
                children.add(node);
            }
        }
        return children;
    }

    public List<TreeEditorNode> visibleNodes(boolean showHidden) {
        if (showHidden) {
            return List.copyOf(this.nodes);
        }
        List<TreeEditorNode> visible = new ArrayList<>();
        for (TreeEditorNode node : this.nodes) {
            if (!node.isHiddenInGui()) {
                visible.add(node);
            }
        }
        return visible;
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

    public static String sanitizeFileName(String name) {
        String trimmed = name == null ? "" : name.trim();
        trimmed = trimmed.replace('\\', '_').replace('/', '_');
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(".json")) {
            trimmed = trimmed.substring(0, trimmed.length() - 5);
        }
        return trimmed.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private boolean wouldCycle(TreeEditorNode child, TreeEditorNode parent) {
        ArrayDeque<TreeEditorNode> queue = new ArrayDeque<>();
        queue.add(parent);
        Set<String> seen = new HashSet<>();
        int guard = 0;
        while (!queue.isEmpty() && guard++ < 256) {
            TreeEditorNode current = queue.poll();
            if (current == null || !seen.add(current.getKey())) {
                continue;
            }
            if (current == child) {
                return true;
            }
            for (String key : current.getParentKeys()) {
                TreeEditorNode next = find(key);
                if (next != null) {
                    queue.add(next);
                }
            }
        }
        return false;
    }

    private String uniqueKey(String base) {
        String sanitized = sanitizeKey(base);
        if (find(sanitized) == null) {
            return sanitized;
        }
        int index = 2;
        while (find(sanitized + "_" + index) != null) {
            index++;
        }
        return sanitized + "_" + index;
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
}
