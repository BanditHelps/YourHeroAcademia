package com.github.bandithelps.gui.tree;

import com.github.bandithelps.utils.tree.ConnectionPathProperties;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import net.threetag.palladium.power.ability.AbilityProperties;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-parent {@code gui_connection} waypoints. A JSON array is the legacy single-path form;
 * an object maps parent ability keys to waypoint lists.
 */
public final class TreeConnectionPaths {
    public static final String JSON_KEY = TreeConnectionPath.JSON_KEY;
    public static final String LEGACY_KEY = "";
    public static final TreeConnectionPaths EMPTY = new TreeConnectionPaths(Map.of());

    public static final Codec<TreeConnectionPaths> CODEC = Codec.either(
            TreeConnectionPath.CODEC,
            Codec.unboundedMap(Codec.STRING, TreeConnectionPath.CODEC)
    ).xmap(
            either -> either.map(TreeConnectionPaths::ofLegacy, TreeConnectionPaths::of),
            paths -> paths.isLegacyArray()
                    ? Either.left(paths.get(LEGACY_KEY))
                    : Either.right(paths.asMap())
    );

    private final LinkedHashMap<String, TreeConnectionPath> byParent;

    public TreeConnectionPaths(Map<String, TreeConnectionPath> byParent) {
        this.byParent = new LinkedHashMap<>();
        if (byParent != null) {
            for (var entry : byParent.entrySet()) {
                if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                    this.byParent.put(entry.getKey() == null ? LEGACY_KEY : entry.getKey(), entry.getValue().copy());
                }
            }
        }
    }

    public static TreeConnectionPaths ofLegacy(TreeConnectionPath path) {
        if (path == null || path.isEmpty()) {
            return EMPTY;
        }
        return new TreeConnectionPaths(Map.of(LEGACY_KEY, path));
    }

    public static TreeConnectionPaths of(Map<String, TreeConnectionPath> byParent) {
        TreeConnectionPaths paths = new TreeConnectionPaths(byParent);
        return paths.isEmpty() ? EMPTY : paths;
    }

    public static TreeConnectionPaths fromProperties(AbilityProperties properties) {
        return ConnectionPathProperties.of(properties).yha$getGuiConnections();
    }

    public static TreeConnectionPaths fromJson(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return EMPTY;
        }
        if (element.isJsonArray()) {
            return ofLegacy(TreeConnectionPath.fromJson(element));
        }
        if (!element.isJsonObject()) {
            return EMPTY;
        }
        LinkedHashMap<String, TreeConnectionPath> map = new LinkedHashMap<>();
        for (var entry : element.getAsJsonObject().entrySet()) {
            TreeConnectionPath path = TreeConnectionPath.fromJson(entry.getValue());
            if (!path.isEmpty()) {
                map.put(entry.getKey(), path);
            }
        }
        return of(map);
    }

    public TreeConnectionPaths bindLegacy(@Nullable String firstParentKey) {
        if (firstParentKey == null || firstParentKey.isBlank() || !this.byParent.containsKey(LEGACY_KEY)) {
            return this;
        }
        LinkedHashMap<String, TreeConnectionPath> next = new LinkedHashMap<>(this.byParent);
        TreeConnectionPath legacy = next.remove(LEGACY_KEY);
        if (legacy != null && !legacy.isEmpty() && !next.containsKey(firstParentKey)) {
            next.put(firstParentKey, legacy);
        }
        return of(next);
    }

    public boolean isEmpty() {
        return this.byParent.isEmpty();
    }

    public boolean isLegacyArray() {
        return this.byParent.size() == 1 && this.byParent.containsKey(LEGACY_KEY);
    }

    public TreeConnectionPath get(@Nullable String parentKey) {
        if (parentKey != null && this.byParent.containsKey(parentKey)) {
            return this.byParent.get(parentKey);
        }
        if (this.isLegacyArray()) {
            return this.byParent.get(LEGACY_KEY);
        }
        return TreeConnectionPath.EMPTY;
    }

    public TreeConnectionPaths with(@Nullable String parentKey, @Nullable TreeConnectionPath path) {
        String key = parentKey == null ? LEGACY_KEY : parentKey;
        LinkedHashMap<String, TreeConnectionPath> next = new LinkedHashMap<>(this.byParent);
        if (path == null || path.isEmpty()) {
            next.remove(key);
        } else {
            next.put(key, path);
        }
        return of(next);
    }

    public TreeConnectionPaths without(@Nullable String parentKey) {
        return this.with(parentKey, TreeConnectionPath.EMPTY);
    }

    public TreeConnectionPaths replaceParentKey(String oldKey, String newKey) {
        if (oldKey == null || !this.byParent.containsKey(oldKey)) {
            return this;
        }
        LinkedHashMap<String, TreeConnectionPath> next = new LinkedHashMap<>();
        for (var entry : this.byParent.entrySet()) {
            String key = oldKey.equals(entry.getKey()) ? newKey : entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            next.putIfAbsent(key, entry.getValue());
        }
        return of(next);
    }

    public TreeConnectionPaths copy() {
        return this.isEmpty() ? EMPTY : new TreeConnectionPaths(this.byParent);
    }

    public Map<String, TreeConnectionPath> asMap() {
        return Map.copyOf(this.byParent);
    }

    public JsonElement toJson() {
        if (this.isEmpty()) {
            return TreeConnectionPath.EMPTY.toJson();
        }
        if (this.byParent.size() == 1) {
            return this.byParent.values().iterator().next().toJson();
        }
        JsonObject object = new JsonObject();
        for (var entry : this.byParent.entrySet()) {
            object.add(entry.getKey(), entry.getValue().toJson());
        }
        return object;
    }

    public JsonElement toJson(int parentCount) {
        if (this.isEmpty()) {
            return TreeConnectionPath.EMPTY.toJson();
        }
        if (this.isLegacyArray() || (this.byParent.size() == 1 && parentCount <= 1)) {
            return this.byParent.values().iterator().next().toJson();
        }
        JsonObject object = new JsonObject();
        for (var entry : this.byParent.entrySet()) {
            if (!LEGACY_KEY.equals(entry.getKey())) {
                object.add(entry.getKey(), entry.getValue().toJson());
            }
        }
        return object.size() == 0 ? this.byParent.values().iterator().next().toJson() : object;
    }
}
