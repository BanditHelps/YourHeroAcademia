package com.github.bandithelps.gui.tree;

import net.threetag.palladium.icon.Icon;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class TreeEditorNode {
    private String key;
    private final String originalKey;
    private String title;
    private final String originalTitle;
    private String description;
    private final String originalDescription;
    private String lockedDescription;
    private final String originalLockedDescription;
    @Nullable
    private Icon icon;
    private final String originalIconId;
    private float gridX;
    private float gridY;
    private final float originalGridX;
    private final float originalGridY;
    @Nullable
    private String parentKey;
    @Nullable
    private final String originalParentKey;
    private TreeEditorCostDraft cost;
    private final TreeEditorCostDraft originalCost;
    private TreeConnectionPath connectionPath;
    private final TreeConnectionPath originalConnectionPath;
    private final boolean created;

    public TreeEditorNode(
            String key,
            String title,
            String description,
            String lockedDescription,
            @Nullable Icon icon,
            float gridX,
            float gridY,
            @Nullable String parentKey,
            TreeEditorCostDraft cost,
            TreeConnectionPath connectionPath,
            boolean created
    ) {
        this.key = key;
        this.originalKey = key;
        this.title = title;
        this.originalTitle = title;
        this.description = description == null ? "" : description;
        this.originalDescription = this.description;
        this.lockedDescription = lockedDescription == null ? "" : lockedDescription;
        this.originalLockedDescription = this.lockedDescription;
        this.icon = icon;
        this.originalIconId = this.getIconId();
        this.gridX = gridX;
        this.gridY = gridY;
        this.originalGridX = gridX;
        this.originalGridY = gridY;
        this.parentKey = parentKey;
        this.originalParentKey = parentKey;
        this.cost = cost == null ? TreeEditorCostDraft.none() : cost;
        this.originalCost = this.cost.copy();
        this.connectionPath = connectionPath == null ? TreeConnectionPath.EMPTY : connectionPath.copy();
        this.originalConnectionPath = this.connectionPath.copy();
        this.created = created;
    }

    public static TreeEditorNode created(String key, String title, float gridX, float gridY) {
        return new TreeEditorNode(key, title, "", "", parseIcon("minecraft:paper"), gridX, gridY, null, TreeEditorCostDraft.none(), TreeConnectionPath.EMPTY, true);
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

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
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
        return this.icon;
    }

    public void setIcon(@Nullable Icon icon) {
        this.icon = icon;
    }

    public void setIconId(String iconId) {
        this.icon = parseIcon(iconId);
    }

    public String getIconId() {
        if (this.icon == null) {
            return "minecraft:paper";
        }
        String simple = Icon.toSimpleString(this.icon);
        return simple == null || simple.isBlank() ? "minecraft:paper" : simple;
    }

    public float getGridX() {
        return this.gridX;
    }

    public float getGridY() {
        return this.gridY;
    }

    public void setGrid(float gridX, float gridY) {
        this.gridX = gridX;
        this.gridY = gridY;
    }

    public float getOriginalGridX() {
        return this.originalGridX;
    }

    public float getOriginalGridY() {
        return this.originalGridY;
    }

    @Nullable
    public String getParentKey() {
        return this.parentKey;
    }

    public void setParentKey(@Nullable String parentKey) {
        this.parentKey = parentKey;
    }

    @Nullable
    public String getOriginalParentKey() {
        return this.originalParentKey;
    }

    public TreeEditorCostDraft getCost() {
        return this.cost;
    }

    public void setCost(TreeEditorCostDraft cost) {
        this.cost = cost == null ? TreeEditorCostDraft.none() : cost;
    }

    public TreeConnectionPath getConnectionPath() {
        return this.connectionPath;
    }

    public void setConnectionPath(TreeConnectionPath connectionPath) {
        this.connectionPath = connectionPath == null ? TreeConnectionPath.EMPTY : connectionPath;
    }

    public boolean isCreated() {
        return this.created;
    }

    public boolean positionChanged() {
        return Float.compare(this.gridX, this.originalGridX) != 0
                || Float.compare(this.gridY, this.originalGridY) != 0;
    }

    public boolean parentChanged() {
        return !Objects.equals(this.parentKey, this.originalParentKey);
    }

    public boolean metadataChanged() {
        return !Objects.equals(this.title, this.originalTitle)
                || !Objects.equals(this.description, this.originalDescription)
                || !Objects.equals(this.lockedDescription, this.originalLockedDescription)
                || !Objects.equals(this.getIconId(), this.originalIconId);
    }

    public boolean costChanged() {
        return !this.cost.sameAs(this.originalCost);
    }

    public boolean connectionChanged() {
        return !this.connectionPath.equals(this.originalConnectionPath);
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
}
