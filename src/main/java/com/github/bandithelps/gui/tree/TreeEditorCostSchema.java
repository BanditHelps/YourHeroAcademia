package com.github.bandithelps.gui.tree;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class TreeEditorCostSchema {
    public static final String NONE_ID = "none";
    public static final TreeEditorCostSchema NONE = new TreeEditorCostSchema(NONE_ID, "None", List.of());
    private static final Set<String> HIDDEN_IDS = Set.of(
            "palladium:number_data_attachment"
    );

    private final String id;
    private final String name;
    private final List<Field> fields;

    public TreeEditorCostSchema(String id, String name, List<Field> fields) {
        this.id = id;
        this.name = name;
        this.fields = List.copyOf(fields);
    }

    public String id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public List<Field> fields() {
        return this.fields;
    }

    public static List<TreeEditorCostSchema> load(Minecraft minecraft) {
        Path file = minecraft.gameDirectory.toPath()
                .resolve("palladium")
                .resolve("documentation")
                .resolve("export")
                .resolve("palladium")
                .resolve("cost_serializer.json");
        if (Files.isRegularFile(file)) {
            try (Reader reader = Files.newBufferedReader(file)) {
                List<TreeEditorCostSchema> parsed = parse(JsonParser.parseReader(reader));
                if (!parsed.isEmpty()) {
                    List<TreeEditorCostSchema> withNone = new ArrayList<>();
                    withNone.add(NONE);
                    withNone.addAll(parsed);
                    return withNone;
                }
            } catch (Exception ignored) {
                // Fall back to the known built-in serializers.
            }
        }
        return fallback();
    }

    private static List<TreeEditorCostSchema> parse(JsonElement root) {
        List<TreeEditorCostSchema> schemas = new ArrayList<>();
        if (root == null || !root.isJsonArray()) {
            return schemas;
        }
        for (JsonElement element : root.getAsJsonArray()) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            String namespace = string(object, "namespace");
            String path = string(object, "path");
            if (namespace.isEmpty() || path.isEmpty()) {
                continue;
            }
            String name = object.has("name") ? object.get("name").getAsString() : namespace + ":" + path;
            List<Field> fields = new ArrayList<>();
            if (object.has("fields") && object.get("fields").isJsonArray()) {
                JsonArray array = object.getAsJsonArray("fields");
                for (JsonElement fieldElement : array) {
                    if (!fieldElement.isJsonObject()) {
                        continue;
                    }
                    JsonObject field = fieldElement.getAsJsonObject();
                    String key = string(field, "key");
                    if (key.isEmpty()) {
                        continue;
                    }
                    fields.add(new Field(key, string(field, "type"), string(field, "description")));
                }
            }
            String id = namespace + ":" + path;
            if (HIDDEN_IDS.contains(id)) {
                continue;
            }
            schemas.add(new TreeEditorCostSchema(id, name, fields));
        }
        return schemas;
    }

    private static List<TreeEditorCostSchema> fallback() {
        return List.of(
                NONE,
                new TreeEditorCostSchema("yha:upgrade_point", "Upgrade Point Cost", List.of(
                        new Field("points", "Integer (> 0)", "Upgrade points to consume.")
                )),
                new TreeEditorCostSchema("palladium:experience_level", "Experience Level Cost", List.of(
                        new Field("xp_level", "Integer (> 0)", "Experience levels to consume.")
                )),
                new TreeEditorCostSchema("palladium:item", "Item Cost", List.of(
                        new Field("ingredient", "Ingredient / Item", "Item accepted as payment."),
                        new Field("amount", "Integer (> 0)", "How many items to consume.")
                )),
                new TreeEditorCostSchema("palladium:score", "Score Cost", List.of(
                        new Field("objective", "String", "Scoreboard objective name."),
                        new Field("amount", "Integer (> 0)", "Score amount to consume."),
                        new Field("icon", "Icon definition", "Icon shown in the UI."),
                        new Field("description", "Text Component", "Display text shown in the UI.")
                ))
        );
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    public record Field(String key, String type, String description) {
        public boolean numeric() {
            String lower = this.type.toLowerCase(Locale.ROOT);
            return lower.contains("integer") || lower.contains("float") || lower.contains("number");
        }

        public boolean itemLike() {
            String lower = this.type.toLowerCase(Locale.ROOT);
            return lower.contains("ingredient") || lower.contains("item") || lower.contains("icon");
        }
    }
}
