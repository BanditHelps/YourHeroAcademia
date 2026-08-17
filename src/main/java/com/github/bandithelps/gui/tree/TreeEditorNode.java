package com.github.bandithelps.gui.tree;

import net.threetag.palladium.icon.Icon;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public final class TreeEditorNode {
    private String key;
    private final String originalKey;
    private String title;
    @Nullable
    private final Icon icon;
    private float gridX;
    private float gridY;
    private final float originalGridX;
    private final float originalGridY;
    @Nullable
    private String parentKey;
    @Nullable
    private final String originalParentKey;
    private int costPoints;
    private final boolean created;

    public TreeEditorNode(
            String key,
            String title,
            @Nullable Icon icon,
            float gridX,
            float gridY,
            @Nullable String parentKey,
            int costPoints,
            boolean created
    ) {
        this.key = key;
        this.originalKey = key;
        this.title = title;
        this.icon = icon;
        this.gridX = gridX;
        this.gridY = gridY;
        this.originalGridX = gridX;
        this.originalGridY = gridY;
        this.parentKey = parentKey;
        this.originalParentKey = parentKey;
        this.costPoints = Math.max(1, costPoints);
        this.created = created;
    }

    public static TreeEditorNode created(String key, String title, float gridX, float gridY) {
        return new TreeEditorNode(key, title, null, gridX, gridY, null, 1, true);
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

    @Nullable
    public Icon getIcon() {
        return this.icon;
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

    public int getCostPoints() {
        return this.costPoints;
    }

    public void setCostPoints(int costPoints) {
        this.costPoints = Math.max(1, costPoints);
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
}
