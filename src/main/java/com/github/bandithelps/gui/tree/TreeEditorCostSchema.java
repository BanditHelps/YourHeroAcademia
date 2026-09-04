package com.github.bandithelps.gui.tree;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

public final class TreeEditorCostSchema {
    public static final String NONE_ID = "none";
    public static final TreeEditorCostSchema NONE = new TreeEditorCostSchema(NONE_ID, "None", List.of());

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
        return com.github.bandithelps.gui.tree.schema.PalladiumDocCatalog.load(minecraft).costs();
    }

    public static List<TreeEditorCostSchema> fallbackCosts() {
        return List.of(
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
