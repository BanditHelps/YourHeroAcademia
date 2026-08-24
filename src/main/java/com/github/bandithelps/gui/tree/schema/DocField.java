package com.github.bandithelps.gui.tree.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class DocField {
    private final String key;
    private final JsonElement typeJson;
    private final String typeText;
    private final String description;
    private final boolean required;
    @Nullable
    private final JsonElement fallback;
    private final DocFieldKind kind;
    private final List<String> enumValues;
    private final List<String> combinedOptions;

    public DocField(String key, JsonElement typeJson, String description, boolean required, @Nullable JsonElement fallback) {
        this.key = key;
        this.typeJson = typeJson == null ? null : typeJson.deepCopy();
        this.typeText = stringifyType(typeJson);
        this.description = description == null ? "" : description;
        this.required = required;
        this.fallback = fallback == null || fallback.isJsonNull() ? null : fallback.deepCopy();
        this.enumValues = readEnumValues(typeJson);
        this.combinedOptions = readCombinedOptions(typeJson);
        this.kind = resolveKind(typeJson, this.typeText, this.enumValues, this.combinedOptions);
    }

    public String key() {
        return this.key;
    }

    public String typeText() {
        return this.typeText;
    }

    public String description() {
        return this.description;
    }

    public boolean required() {
        return this.required;
    }

    @Nullable
    public JsonElement fallback() {
        return this.fallback;
    }

    public DocFieldKind kind() {
        return this.kind;
    }

    public List<String> enumValues() {
        return this.enumValues;
    }

    public List<String> combinedOptions() {
        return this.combinedOptions;
    }

    public boolean numeric() {
        return this.kind == DocFieldKind.INTEGER || this.kind == DocFieldKind.FLOAT;
    }

    public boolean itemLike() {
        return this.kind == DocFieldKind.ITEM || this.kind == DocFieldKind.ICON;
    }

    public String label() {
        return this.required ? this.key + "*" : this.key;
    }

    private static DocFieldKind resolveKind(
            @Nullable JsonElement typeJson,
            String typeText,
            List<String> enumValues,
            List<String> combinedOptions
    ) {
        if (!enumValues.isEmpty()) {
            return DocFieldKind.ENUM;
        }
        if (!combinedOptions.isEmpty()) {
            return DocFieldKind.COMBINED;
        }
        if (typeJson != null && typeJson.isJsonObject()) {
            String objectType = string(typeJson.getAsJsonObject(), "type").toLowerCase(Locale.ROOT);
            if ("enum".equals(objectType)) {
                return DocFieldKind.ENUM;
            }
            if ("combined".equals(objectType)) {
                return DocFieldKind.COMBINED;
            }
        }
        return DocFieldKind.classify(typeText);
    }

    private static List<String> readEnumValues(@Nullable JsonElement typeJson) {
        if (typeJson == null || !typeJson.isJsonObject()) {
            return List.of();
        }
        JsonObject object = typeJson.getAsJsonObject();
        if (!"enum".equalsIgnoreCase(string(object, "type")) || !object.has("values") || !object.get("values").isJsonArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray("values")) {
            if (element.isJsonPrimitive()) {
                values.add(element.getAsString());
            }
        }
        return List.copyOf(values);
    }

    private static List<String> readCombinedOptions(@Nullable JsonElement typeJson) {
        if (typeJson == null || !typeJson.isJsonObject()) {
            return List.of();
        }
        JsonObject object = typeJson.getAsJsonObject();
        if (!"combined".equalsIgnoreCase(string(object, "type")) || !object.has("options") || !object.get("options").isJsonArray()) {
            return List.of();
        }
        List<String> options = new ArrayList<>();
        for (JsonElement element : object.getAsJsonArray("options")) {
            if (element.isJsonPrimitive()) {
                options.add(element.getAsString());
            }
        }
        return List.copyOf(options);
    }

    private static String stringifyType(@Nullable JsonElement typeJson) {
        if (typeJson == null || typeJson.isJsonNull()) {
            return "";
        }
        if (typeJson.isJsonPrimitive()) {
            return typeJson.getAsString();
        }
        if (typeJson.isJsonObject()) {
            JsonObject object = typeJson.getAsJsonObject();
            String type = string(object, "type");
            if ("enum".equalsIgnoreCase(type)) {
                return "enum";
            }
            if ("combined".equalsIgnoreCase(type) && object.has("options") && object.get("options").isJsonArray()) {
                JsonArray options = object.getAsJsonArray("options");
                StringBuilder builder = new StringBuilder();
                for (JsonElement option : options) {
                    if (!builder.isEmpty()) {
                        builder.append(" / ");
                    }
                    builder.append(option.isJsonPrimitive() ? option.getAsString() : option.toString());
                }
                return builder.toString();
            }
        }
        return typeJson.toString();
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
}
