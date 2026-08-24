package com.github.bandithelps.gui.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.threetag.palladium.icon.Icon;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TreeEditorNode {
    public static final String DEFAULT_TYPE = "palladium:dummy";

    private String key;
    private final String originalKey;
    private String typeId;
    private String title;
    private String description;
    private String lockedDescription;
    private String iconId;
    private float gridX;
    private float gridY;
    private final List<String> parentKeys = new ArrayList<>();
    private TreeEditorCostDraft cost;
    private TreeConnectionPaths connectionPaths;
    private boolean hiddenInGui;
    private boolean hiddenInBar;
    private int listIndex;
    private int activationStamina;
    private int staminaInterval;
    private int staminaIntervalCost;
    private JsonObject typeFields;
    @Nullable
    private JsonElement unlocking;
    @Nullable
    private JsonElement enabling;
    private final boolean created;
    private final float originalGridX;
    private final float originalGridY;

    public TreeEditorNode(
            String key,
            String typeId,
            String title,
            String description,
            String lockedDescription,
            String iconId,
            float gridX,
            float gridY,
            @Nullable String parentKey,
            TreeEditorCostDraft cost,
            TreeConnectionPaths connectionPaths,
            boolean hiddenInGui,
            boolean hiddenInBar,
            int listIndex,
            int activationStamina,
            int staminaInterval,
            int staminaIntervalCost,
            JsonObject typeFields,
            @Nullable JsonElement unlocking,
            @Nullable JsonElement enabling,
            boolean created
    ) {
        this.key = key;
        this.originalKey = key;
        this.typeId = typeId == null || typeId.isBlank() ? DEFAULT_TYPE : typeId;
        this.title = title == null ? "" : title;
        this.description = description == null ? "" : description;
        this.lockedDescription = lockedDescription == null ? "" : lockedDescription;
        this.iconId = iconId == null || iconId.isBlank() ? "minecraft:paper" : iconId;
        this.gridX = TreeEditorDraft.clean(gridX);
        this.gridY = TreeEditorDraft.clean(gridY);
        this.originalGridX = this.gridX;
        this.originalGridY = this.gridY;
        this.unlocking = copy(unlocking);
        this.setParentKeys(this.unlocking != null
                ? TreeEditorStateSync.parentKeysFromUnlocking(this.unlocking)
                : parentKey == null ? List.of() : List.of(parentKey));
        this.cost = cost == null ? TreeEditorCostDraft.none() : cost;
        this.connectionPaths = connectionPaths == null ? TreeConnectionPaths.EMPTY : connectionPaths.copy();
        this.hiddenInGui = hiddenInGui;
        this.hiddenInBar = hiddenInBar;
        this.listIndex = listIndex;
        this.activationStamina = Math.max(0, activationStamina);
        this.staminaInterval = Math.max(0, staminaInterval);
        this.staminaIntervalCost = Math.max(0, staminaIntervalCost);
        this.typeFields = typeFields == null ? new JsonObject() : typeFields.deepCopy();
        this.enabling = copy(enabling);
        this.created = created;
    }

    public static TreeEditorNode created(String key, String title, float gridX, float gridY) {
        return created(key, DEFAULT_TYPE, title, gridX, gridY);
    }

    public static TreeEditorNode created(String key, String typeId, String title, float gridX, float gridY) {
        return new TreeEditorNode(
                key,
                typeId,
                title,
                "",
                "",
                "minecraft:paper",
                gridX,
                gridY,
                null,
                TreeEditorCostDraft.none(),
                TreeConnectionPaths.EMPTY,
                false,
                false,
                0,
                0,
                0,
                0,
                new JsonObject(),
                null,
                null,
                true
        );
    }

    public static TreeEditorNode fromAbilityJson(String key, JsonObject ability) {
        JsonObject properties = object(ability, "properties");
        JsonObject state = object(ability, "state");
        JsonElement unlocking = state != null && state.has("unlocking") ? state.get("unlocking") : null;
        JsonElement enabling = state != null && state.has("enabling") ? state.get("enabling") : null;
        String typeId = ability.has("type") && ability.get("type").isJsonPrimitive()
                ? ability.get("type").getAsString()
                : DEFAULT_TYPE;
        DescriptionText description = readDescription(properties);
        float[] position = readPosition(properties);
        JsonObject typeFields = new JsonObject();
        for (var entry : ability.entrySet()) {
            if (!"type".equals(entry.getKey()) && !"properties".equals(entry.getKey()) && !"state".equals(entry.getKey())) {
                typeFields.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
        return new TreeEditorNode(
                key,
                typeId,
                string(properties, "title", key),
                description.unlocked(),
                description.locked(),
                readIconId(properties),
                position[0],
                position[1],
                firstKey(TreeEditorStateSync.parentKeysFromUnlocking(unlocking)),
                TreeEditorCostDraft.fromUnlocking(unlocking),
                TreeConnectionPaths.fromJson(properties == null ? null : properties.get(TreeConnectionPaths.JSON_KEY))
                        .bindLegacy(firstKey(TreeEditorStateSync.parentKeysFromUnlocking(unlocking))),
                bool(properties, "hidden_in_gui", false),
                bool(properties, "hidden_in_bar", false),
                integer(properties, "list_index", 0),
                integer(properties, "activation_stamina", 0),
                integer(properties, "stamina_interval", 0),
                integer(properties, "stamina_interval_cost", 0),
                typeFields,
                unlocking,
                enabling,
                false
        );
    }

    public JsonObject toAbilityJson() {
        JsonObject ability = new JsonObject();
        ability.addProperty("type", this.typeId);
        for (var entry : this.typeFields.entrySet()) {
            ability.add(entry.getKey(), entry.getValue().deepCopy());
        }
        JsonObject properties = new JsonObject();
        if (!this.title.isBlank()) {
            properties.addProperty("title", this.title);
        }
        if (!this.description.isBlank() || this.hasSplitDescription()) {
            properties.add("description", descriptionJson());
        }
        properties.addProperty("icon", this.iconId);
        properties.addProperty("hidden_in_gui", this.hiddenInGui);
        properties.addProperty("hidden_in_bar", this.hiddenInBar);
        properties.add("gui_position", positionArray(this.gridX, this.gridY));
        if (this.listIndex != 0) {
            properties.addProperty("list_index", this.listIndex);
        }
        if (this.activationStamina > 0) {
            properties.addProperty("activation_stamina", this.activationStamina);
        }
        if (this.staminaInterval > 0) {
            properties.addProperty("stamina_interval", this.staminaInterval);
        }
        if (this.staminaIntervalCost > 0) {
            properties.addProperty("stamina_interval_cost", this.staminaIntervalCost);
        }
        if (!this.connectionPaths.isEmpty()) {
            properties.add(TreeConnectionPaths.JSON_KEY, this.connectionPaths.toJson(this.parentKeys.size()));
        }
        ability.add("properties", properties);

        JsonObject state = new JsonObject();
        if (this.unlocking != null && !isEmpty(this.unlocking)) {
            state.add("unlocking", this.unlocking.deepCopy());
        }
        if (this.enabling != null && !isEmpty(this.enabling)) {
            state.add("enabling", this.enabling.deepCopy());
        }
        if (state.size() > 0) {
            ability.add("state", state);
        }
        return ability;
    }

    public String getKey() {
        return this.key;
    }

    public String getOriginalKey() {
        return this.originalKey;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getTypeId() {
        return this.typeId;
    }

    public void setTypeId(String typeId) {
        this.typeId = typeId == null || typeId.isBlank() ? DEFAULT_TYPE : typeId;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title == null ? "" : title;
    }

    public String getDescription() {
        return this.description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public String getLockedDescription() {
        return this.lockedDescription;
    }

    public void setLockedDescription(String lockedDescription) {
        this.lockedDescription = lockedDescription == null ? "" : lockedDescription;
    }

    public boolean hasSplitDescription() {
        return !this.lockedDescription.isBlank() && !this.lockedDescription.equals(this.description);
    }

    @Nullable
    public Icon getIcon() {
        return parseIcon(this.iconId);
    }

    public void setIcon(@Nullable Icon icon) {
        if (icon == null) {
            this.iconId = "minecraft:paper";
            return;
        }
        String simple = Icon.toSimpleString(icon);
        this.iconId = simple == null || simple.isBlank() ? "minecraft:paper" : simple;
    }

    public void setIconId(String iconId) {
        this.iconId = iconId == null || iconId.isBlank() ? "minecraft:paper" : iconId;
    }

    public String getIconId() {
        return this.iconId;
    }

    public float getGridX() {
        return this.gridX;
    }

    public float getGridY() {
        return this.gridY;
    }

    public void setGrid(float gridX, float gridY) {
        this.gridX = TreeEditorDraft.clean(gridX);
        this.gridY = TreeEditorDraft.clean(gridY);
    }

    public float getOriginalGridX() {
        return this.originalGridX;
    }

    public float getOriginalGridY() {
        return this.originalGridY;
    }

    public List<String> getParentKeys() {
        return this.parentKeys;
    }

    @Nullable
    public String firstParentKey() {
        return this.parentKeys.isEmpty() ? null : this.parentKeys.getFirst();
    }

    @Nullable
    public String getParentKey() {
        return this.firstParentKey();
    }

    public boolean hasParent(@Nullable String parentKey) {
        return parentKey != null && this.parentKeys.contains(parentKey);
    }

    public void setParentKeys(@Nullable List<String> parentKeys) {
        this.parentKeys.clear();
        if (parentKeys == null) {
            return;
        }
        for (String key : parentKeys) {
            if (key != null && !key.isBlank() && !this.parentKeys.contains(key)) {
                this.parentKeys.add(key);
            }
        }
    }

    public void setParentKey(@Nullable String parentKey) {
        this.setParentKeys(parentKey == null || parentKey.isBlank() ? List.of() : List.of(parentKey));
    }

    public void replaceParentKey(String oldKey, String newKey) {
        int index = this.parentKeys.indexOf(oldKey);
        if (index < 0) {
            return;
        }
        this.parentKeys.remove(index);
        if (newKey != null && !newKey.isBlank() && !this.parentKeys.contains(newKey)) {
            this.parentKeys.add(index, newKey);
        }
    }

    public TreeEditorCostDraft getCost() {
        return this.cost;
    }

    public void setCost(TreeEditorCostDraft cost) {
        this.cost = cost == null ? TreeEditorCostDraft.none() : cost;
    }

    public TreeConnectionPaths getConnectionPaths() {
        return this.connectionPaths;
    }

    public TreeConnectionPath getConnectionPath() {
        return this.getConnectionPath(this.firstParentKey());
    }

    public TreeConnectionPath getConnectionPath(@Nullable String parentKey) {
        return this.connectionPaths.get(parentKey);
    }

    public void setConnectionPaths(TreeConnectionPaths connectionPaths) {
        this.connectionPaths = connectionPaths == null ? TreeConnectionPaths.EMPTY : connectionPaths;
    }

    public void setConnectionPath(TreeConnectionPath connectionPath) {
        this.setConnectionPath(this.firstParentKey(), connectionPath);
    }

    public void setConnectionPath(@Nullable String parentKey, TreeConnectionPath connectionPath) {
        this.connectionPaths = this.connectionPaths.with(parentKey, connectionPath);
    }

    public void clearConnectionPaths() {
        this.connectionPaths = TreeConnectionPaths.EMPTY;
    }

    public void removeConnectionPath(@Nullable String parentKey) {
        this.connectionPaths = this.connectionPaths.without(parentKey);
    }

    public boolean isHiddenInGui() {
        return this.hiddenInGui;
    }

    public void setHiddenInGui(boolean hiddenInGui) {
        this.hiddenInGui = hiddenInGui;
    }

    public boolean isHiddenInBar() {
        return this.hiddenInBar;
    }

    public void setHiddenInBar(boolean hiddenInBar) {
        this.hiddenInBar = hiddenInBar;
    }

    public int getListIndex() {
        return this.listIndex;
    }

    public void setListIndex(int listIndex) {
        this.listIndex = listIndex;
    }

    public int getActivationStamina() {
        return this.activationStamina;
    }

    public void setActivationStamina(int activationStamina) {
        this.activationStamina = Math.max(0, activationStamina);
    }

    public int getStaminaInterval() {
        return this.staminaInterval;
    }

    public void setStaminaInterval(int staminaInterval) {
        this.staminaInterval = Math.max(0, staminaInterval);
    }

    public int getStaminaIntervalCost() {
        return this.staminaIntervalCost;
    }

    public void setStaminaIntervalCost(int staminaIntervalCost) {
        this.staminaIntervalCost = Math.max(0, staminaIntervalCost);
    }

    public JsonObject getTypeFields() {
        return this.typeFields;
    }

    public void setTypeFields(JsonObject typeFields) {
        this.typeFields = typeFields == null ? new JsonObject() : typeFields.deepCopy();
    }

    @Nullable
    public JsonElement getUnlocking() {
        return this.unlocking;
    }

    public void setUnlocking(@Nullable JsonElement unlocking) {
        this.unlocking = copy(unlocking);
        this.setParentKeys(TreeEditorStateSync.parentKeysFromUnlocking(this.unlocking));
        this.cost = TreeEditorCostDraft.fromUnlocking(this.unlocking);
    }

    @Nullable
    public JsonElement getEnabling() {
        return this.enabling;
    }

    public void setEnabling(@Nullable JsonElement enabling) {
        this.enabling = copy(enabling);
    }

    public boolean isCreated() {
        return this.created;
    }

    public boolean positionChanged() {
        return Float.compare(this.gridX, this.originalGridX) != 0
                || Float.compare(this.gridY, this.originalGridY) != 0;
    }

    public String typeSummary() {
        return this.typeId;
    }

    @Nullable
    public static Icon parseIcon(String iconId) {
        if (iconId == null || iconId.isBlank()) {
            return null;
        }
        try {
            return Icon.parse(iconId);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private JsonElement descriptionJson() {
        if (this.hasSplitDescription()) {
            JsonObject description = new JsonObject();
            description.addProperty("unlocked", this.description);
            description.addProperty("locked", this.lockedDescription);
            return description;
        }
        return new JsonPrimitive(this.description);
    }

    private static JsonArray positionArray(float x, float y) {
        JsonArray array = new JsonArray();
        array.add(TreeEditorDraft.gridNumber(x));
        array.add(TreeEditorDraft.gridNumber(y));
        return array;
    }

    @Nullable
    private static String firstKey(List<String> keys) {
        return keys == null || keys.isEmpty() ? null : keys.getFirst();
    }

    @Nullable
    static JsonObject asObject(@Nullable JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    @Nullable
    private static JsonObject object(JsonObject parent, String key) {
        if (parent == null || !parent.has(key) || !parent.get(key).isJsonObject()) {
            return null;
        }
        return parent.getAsJsonObject(key);
    }

    private static String string(@Nullable JsonObject object, String key, String fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        return object.get(key).getAsString();
    }

    private static boolean bool(@Nullable JsonObject object, String key, boolean fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(key).getAsBoolean();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static int integer(@Nullable JsonObject object, String key, int fallback) {
        if (object == null || !object.has(key) || !object.get(key).isJsonPrimitive()) {
            return fallback;
        }
        try {
            return object.get(key).getAsInt();
        } catch (RuntimeException ignored) {
            return fallback;
        }
    }

    private static float[] readPosition(@Nullable JsonObject properties) {
        if (properties == null || !properties.has("gui_position") || !properties.get("gui_position").isJsonArray()) {
            return new float[]{0.0F, 0.0F};
        }
        JsonArray array = properties.getAsJsonArray("gui_position");
        float x = array.size() > 0 && array.get(0).isJsonPrimitive() ? array.get(0).getAsFloat() : 0.0F;
        float y = array.size() > 1 && array.get(1).isJsonPrimitive() ? array.get(1).getAsFloat() : 0.0F;
        return new float[]{x, y};
    }

    private static String readIconId(@Nullable JsonObject properties) {
        if (properties == null || !properties.has("icon")) {
            return "minecraft:paper";
        }
        JsonElement icon = properties.get("icon");
        if (icon.isJsonPrimitive()) {
            return icon.getAsString();
        }
        return icon.toString();
    }

    private static DescriptionText readDescription(@Nullable JsonObject properties) {
        if (properties == null || !properties.has("description")) {
            return DescriptionText.EMPTY;
        }
        JsonElement element = properties.get("description");
        if (element.isJsonPrimitive()) {
            return new DescriptionText(element.getAsString(), "");
        }
        if (!element.isJsonObject()) {
            return DescriptionText.EMPTY;
        }
        JsonObject object = element.getAsJsonObject();
        String unlocked = string(object, "unlocked", "");
        String locked = string(object, "locked", "");
        if (unlocked.isBlank()) {
            unlocked = locked;
        }
        return new DescriptionText(unlocked, locked);
    }

    private static boolean isEmpty(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return true;
        }
        if (element.isJsonObject()) {
            return element.getAsJsonObject().size() == 0;
        }
        if (element.isJsonArray()) {
            return element.getAsJsonArray().isEmpty();
        }
        return false;
    }

    @Nullable
    private static JsonElement copy(@Nullable JsonElement element) {
        return element == null ? null : element.deepCopy();
    }

    private record DescriptionText(String unlocked, String locked) {
        private static final DescriptionText EMPTY = new DescriptionText("", "");
    }
}
