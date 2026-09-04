package com.github.bandithelps.gui.tree.schema;

import com.github.bandithelps.gui.tree.TreeEditorCostSchema;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.Nullable;

import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class PalladiumDocCatalog {
    private static final Set<String> HIDDEN_COST_IDS = Set.of("palladium:number_data_attachment");

    private final List<DocSchema> abilities;
    private final List<DocSchema> conditions;
    private final List<DocSchema> actions;
    private final List<DocSchema> values;
    private final List<DocSchema> icons;
    private final List<TreeEditorCostSchema> costs;

    public PalladiumDocCatalog(
            List<DocSchema> abilities,
            List<DocSchema> conditions,
            List<DocSchema> actions,
            List<DocSchema> values,
            List<DocSchema> icons,
            List<TreeEditorCostSchema> costs
    ) {
        this.abilities = List.copyOf(abilities);
        this.conditions = List.copyOf(conditions);
        this.actions = List.copyOf(actions);
        this.values = List.copyOf(values);
        this.icons = List.copyOf(icons);
        this.costs = List.copyOf(costs);
    }

    public static Path docsDirectory(Minecraft minecraft) {
        return minecraft.gameDirectory.toPath()
                .resolve("palladium")
                .resolve("documentation")
                .resolve("export")
                .resolve("palladium");
    }

    public static PalladiumDocCatalog load(Minecraft minecraft) {
        Path dir = docsDirectory(minecraft);
        List<DocSchema> abilities = loadSchemas(dir.resolve("ability_serializer.json"));
        List<DocSchema> conditions = loadSchemas(dir.resolve("condition_serializer.json"));
        List<DocSchema> actions = loadSchemas(dir.resolve("action_serializer.json"));
        List<DocSchema> values = loadSchemas(dir.resolve("value_serializer.json"));
        List<DocSchema> icons = loadSchemas(dir.resolve("icon_serializer.json"));
        List<TreeEditorCostSchema> costs = loadCosts(dir.resolve("cost_serializer.json"));
        return new PalladiumDocCatalog(abilities, conditions, actions, values, icons, costs);
    }

    public List<DocSchema> abilities() {
        return this.abilities;
    }

    public List<DocSchema> conditions() {
        return this.conditions;
    }

    public List<DocSchema> actions() {
        return this.actions;
    }

    public List<DocSchema> values() {
        return this.values;
    }

    public List<DocSchema> icons() {
        return this.icons;
    }

    public List<TreeEditorCostSchema> costs() {
        return this.costs;
    }

    @Nullable
    public DocSchema findAbility(String id) {
        return find(this.abilities, id);
    }

    @Nullable
    public DocSchema findCondition(String id) {
        return find(this.conditions, id);
    }

    @Nullable
    public DocSchema findAction(String id) {
        return find(this.actions, id);
    }

    @Nullable
    public DocSchema findValue(String id) {
        return find(this.values, id);
    }

    @Nullable
    public static DocSchema find(List<DocSchema> schemas, @Nullable String id) {
        if (id == null || id.isBlank()) {
            return null;
        }
        for (DocSchema schema : schemas) {
            if (schema.id().equals(id)) {
                return schema;
            }
        }
        return null;
    }

    public static List<DocSchema> loadSchemas(Path file) {
        JsonElement root = readJson(file);
        if (root == null || !root.isJsonArray()) {
            return List.of();
        }
        List<DocSchema> schemas = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            DocSchema schema = parseSchema(element);
            if (schema != null) {
                schemas.add(schema);
            }
        }
        return schemas;
    }

    private static List<TreeEditorCostSchema> loadCosts(Path file) {
        JsonElement root = readJson(file);
        List<TreeEditorCostSchema> parsed = parseCosts(root);
        List<TreeEditorCostSchema> withNone = new ArrayList<>();
        withNone.add(TreeEditorCostSchema.NONE);
        if (parsed.isEmpty()) {
            withNone.addAll(TreeEditorCostSchema.fallbackCosts());
            return withNone;
        }
        withNone.addAll(parsed);
        return withNone;
    }

    public static List<TreeEditorCostSchema> parseCosts(@Nullable JsonElement root) {
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
            String id = namespace + ":" + path;
            if (HIDDEN_COST_IDS.contains(id)) {
                continue;
            }
            String name = object.has("name") ? object.get("name").getAsString() : id;
            List<TreeEditorCostSchema.Field> fields = new ArrayList<>();
            for (DocField field : parseFields(object)) {
                fields.add(new TreeEditorCostSchema.Field(field.key(), field.typeText(), field.description()));
            }
            schemas.add(new TreeEditorCostSchema(id, name, fields));
        }
        return schemas;
    }

    @Nullable
    private static DocSchema parseSchema(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();
        String namespace = string(object, "namespace");
        String path = string(object, "path");
        if (namespace.isEmpty() || path.isEmpty()) {
            return null;
        }
        String id = namespace + ":" + path;
        String name = object.has("name") ? object.get("name").getAsString() : id;
        String description = string(object, "description");
        JsonElement example = firstExample(object);
        return new DocSchema(id, name, description, parseFields(object), example);
    }

    private static List<DocField> parseFields(JsonObject object) {
        List<DocField> fields = new ArrayList<>();
        if (!object.has("fields") || !object.get("fields").isJsonArray()) {
            return fields;
        }
        for (JsonElement fieldElement : object.getAsJsonArray("fields")) {
            if (!fieldElement.isJsonObject()) {
                continue;
            }
            JsonObject field = fieldElement.getAsJsonObject();
            String key = string(field, "key");
            if (key.isEmpty()) {
                continue;
            }
            boolean required = field.has("required") && field.get("required").isJsonPrimitive() && field.get("required").getAsBoolean();
            JsonElement fallback = field.has("fallback") ? field.get("fallback") : null;
            fields.add(new DocField(key, field.get("type"), string(field, "description"), required, fallback));
        }
        return fields;
    }

    @Nullable
    private static JsonElement firstExample(JsonObject object) {
        if (!object.has("examples") || !object.get("examples").isJsonArray()) {
            return null;
        }
        JsonArray examples = object.getAsJsonArray("examples");
        return examples.isEmpty() ? null : examples.get(0);
    }

    @Nullable
    private static JsonElement readJson(Path file) {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try (Reader reader = Files.newBufferedReader(file)) {
            return JsonParser.parseReader(reader);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }
}
