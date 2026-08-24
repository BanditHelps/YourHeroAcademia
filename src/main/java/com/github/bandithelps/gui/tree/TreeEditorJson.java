package com.github.bandithelps.gui.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class TreeEditorJson {
    private TreeEditorJson() {
    }

    public static String summary(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "None";
        }
        if (element.isJsonPrimitive()) {
            String text = element.getAsString();
            return text.length() > 28 ? text.substring(0, 25) + "..." : text;
        }
        if (element.isJsonArray()) {
            return "List (" + element.getAsJsonArray().size() + ")";
        }
        if (element.isJsonObject()) {
            JsonObject object = element.getAsJsonObject();
            if (object.has("type") && object.get("type").isJsonPrimitive()) {
                return object.get("type").getAsString();
            }
            return "Object";
        }
        return "Value";
    }

    public static String readType(@Nullable JsonElement element) {
        JsonObject object = asObject(element);
        if (object == null || !object.has("type") || !object.get("type").isJsonPrimitive()) {
            return "";
        }
        return object.get("type").getAsString();
    }

    @Nullable
    public static JsonObject asObject(@Nullable JsonElement element) {
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    public static JsonObject objectOrNew(@Nullable JsonElement element) {
        JsonObject object = asObject(element);
        return object == null ? new JsonObject() : object.deepCopy();
    }

    public static JsonArray arrayOrNew(@Nullable JsonElement element) {
        if (element != null && element.isJsonArray()) {
            return element.getAsJsonArray().deepCopy();
        }
        JsonArray array = new JsonArray();
        if (element != null && element.isJsonObject()) {
            array.add(element.deepCopy());
        }
        return array;
    }

    public static List<JsonElement> asConditionList(@Nullable JsonElement element) {
        List<JsonElement> list = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return list;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (child != null && !child.isJsonNull()) {
                    list.add(child.deepCopy());
                }
            }
            return list;
        }
        list.add(element.deepCopy());
        return list;
    }

    public static List<JsonElement> asEnablingList(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return new ArrayList<>();
        }
        if (element.isJsonArray()) {
            return asConditionList(element);
        }
        JsonObject object = asObject(element);
        if (object != null && TreeEditorStateSync.TYPE_AND.equals(readType(object))) {
            return asConditionList(object.get("conditions"));
        }
        return asConditionList(element);
    }

    @Nullable
    public static JsonElement fromConditionList(@Nullable List<JsonElement> list) {
        return fromConditionList(list, false);
    }

    @Nullable
    public static JsonElement fromEnablingList(@Nullable List<JsonElement> list) {
        return fromConditionList(list, true);
    }

    @Nullable
    public static JsonElement fromConditionList(@Nullable List<JsonElement> list, boolean wrapAnd) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        List<JsonElement> cleaned = new ArrayList<>();
        for (JsonElement element : list) {
            if (element != null && !element.isJsonNull()) {
                cleaned.add(element);
            }
        }
        if (cleaned.isEmpty()) {
            return null;
        }
        if (cleaned.size() == 1) {
            return cleaned.getFirst();
        }
        if (wrapAnd) {
            JsonObject and = new JsonObject();
            and.addProperty("type", TreeEditorStateSync.TYPE_AND);
            and.add("conditions", toJsonArray(cleaned));
            return and;
        }
        JsonArray array = toJsonArray(cleaned);
        return array.size() == 1 ? array.get(0) : array;
    }

    public static JsonArray toJsonArray(List<JsonElement> list) {
        JsonArray array = new JsonArray();
        if (list != null) {
            for (JsonElement element : list) {
                if (element != null && !element.isJsonNull()) {
                    array.add(element);
                }
            }
        }
        return array;
    }

    public static JsonObject typedObject(String type, @Nullable JsonElement example) {
        if (example != null && example.isJsonObject()) {
            JsonObject object = example.getAsJsonObject().deepCopy();
            object.addProperty("type", type);
            return object;
        }
        JsonObject object = new JsonObject();
        object.addProperty("type", type);
        return object;
    }

    public static void putString(JsonObject object, String key, String value) {
        if (value == null || value.isBlank()) {
            object.remove(key);
        } else {
            object.addProperty(key, value);
        }
    }

    public static void putNumber(JsonObject object, String key, String value, boolean integer) {
        if (value == null || value.isBlank()) {
            object.remove(key);
            return;
        }
        try {
            if (integer) {
                object.addProperty(key, Integer.parseInt(value.trim()));
            } else {
                object.addProperty(key, Double.parseDouble(value.trim()));
            }
        } catch (NumberFormatException ignored) {
            object.addProperty(key, value);
        }
    }

    public static void putBoolean(JsonObject object, String key, boolean value) {
        object.addProperty(key, value);
    }

    public static String asText(@Nullable JsonElement element) {
        if (element == null || element.isJsonNull()) {
            return "";
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return element.toString();
    }

    public static JsonPrimitive primitive(String value) {
        return new JsonPrimitive(value == null ? "" : value);
    }
}
