package com.github.bandithelps.gui.tree.schema;

import com.google.gson.JsonElement;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class DocSchema {
    private final String id;
    private final String name;
    private final String description;
    private final List<DocField> fields;
    @Nullable
    private final JsonElement example;

    public DocSchema(String id, String name, String description, List<DocField> fields, @Nullable JsonElement example) {
        this.id = id;
        this.name = name == null || name.isBlank() ? id : name;
        this.description = description == null ? "" : description;
        this.fields = List.copyOf(fields);
        this.example = example == null ? null : example.deepCopy();
    }

    public String id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public String description() {
        return this.description;
    }

    public List<DocField> fields() {
        return this.fields;
    }

    @Nullable
    public JsonElement example() {
        return this.example;
    }

    @Nullable
    public DocField field(String key) {
        for (DocField field : this.fields) {
            if (field.key().equals(key)) {
                return field;
            }
        }
        return null;
    }
}
