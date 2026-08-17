package com.github.bandithelps.gui.tree;

import com.github.bandithelps.conditions.cost.UpgradePointCost;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec2;
import net.threetag.palladium.icon.Icon;
import net.threetag.palladium.power.Power;
import net.threetag.palladium.power.ability.Ability;
import net.threetag.palladium.power.ability.AbilityReference;
import net.threetag.palladium.power.ability.unlocking.BuyableUnlockingHandler;
import net.threetag.palladium.power.ability.unlocking.UnlockingHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class TreeEditorDraft {
    public static final float GRID_SNAP = 0.25F;

    private final Identifier powerId;
    private final String powerName;
    private final List<TreeEditorNode> nodes = new ArrayList<>();

    public TreeEditorDraft(Identifier powerId, String powerName) {
        this.powerId = powerId;
        this.powerName = powerName;
    }

    public static TreeEditorDraft fromPower(Identifier powerId, Power power) {
        TreeEditorDraft draft = new TreeEditorDraft(powerId, power.getName().getString());
        for (var entry : power.getAbilities().entrySet()) {
            Ability ability = entry.getValue();
            if (ability.getProperties().isHiddenInGUI()) {
                continue;
            }
            Vec2 position = ability.getProperties().getGuiPosition();
            float gridX = position == null ? 0.0F : position.x;
            float gridY = position == null ? 0.0F : position.y;
            String parentKey = firstParentKey(ability);
            int costPoints = readCostPoints(ability);
            Icon icon = ability.getProperties().getIcon();
            String title = ability.getDisplayName().getString();
            draft.nodes.add(new TreeEditorNode(
                    entry.getKey(),
                    title,
                    icon,
                    gridX,
                    gridY,
                    parentKey,
                    costPoints,
                    false
            ));
        }
        return draft;
    }

    public Identifier getPowerId() {
        return this.powerId;
    }

    public String getPowerName() {
        return this.powerName;
    }

    public List<TreeEditorNode> getNodes() {
        return this.nodes;
    }

    @Nullable
    public TreeEditorNode find(String key) {
        if (key == null || key.isBlank()) {
            return null;
        }
        for (TreeEditorNode node : this.nodes) {
            if (node.getKey().equals(key)) {
                return node;
            }
        }
        return null;
    }

    public TreeEditorNode addDummy(float gridX, float gridY) {
        String key = uniqueKey("new_node");
        TreeEditorNode node = TreeEditorNode.created(key, "New Node", snap(gridX), snap(gridY));
        this.nodes.add(node);
        return node;
    }

    public boolean rename(TreeEditorNode node, String newKey) {
        String trimmed = sanitizeKey(newKey);
        if (trimmed.isEmpty()) {
            return false;
        }
        if (!node.isCreated()) {
            return false;
        }
        if (trimmed.equals(node.getKey())) {
            return true;
        }
        if (find(trimmed) != null) {
            return false;
        }
        String oldKey = node.getKey();
        node.setKey(trimmed);
        for (TreeEditorNode other : this.nodes) {
            if (oldKey.equals(other.getParentKey())) {
                other.setParentKey(trimmed);
            }
        }
        return true;
    }

    public boolean setParent(TreeEditorNode child, @Nullable TreeEditorNode parent) {
        if (parent == null) {
            child.setParentKey(null);
            return true;
        }
        if (child == parent || wouldCycle(child, parent)) {
            return false;
        }
        child.setParentKey(parent.getKey());
        return true;
    }

    public boolean remove(TreeEditorNode node) {
        if (!node.isCreated()) {
            return false;
        }
        this.nodes.remove(node);
        for (TreeEditorNode other : this.nodes) {
            if (node.getKey().equals(other.getParentKey())) {
                other.setParentKey(null);
            }
        }
        return true;
    }

    public List<TreeEditorNode> childrenOf(TreeEditorNode parent) {
        List<TreeEditorNode> children = new ArrayList<>();
        for (TreeEditorNode node : this.nodes) {
            if (parent.getKey().equals(node.getParentKey())) {
                children.add(node);
            }
        }
        return children;
    }

    public static float snap(float value) {
        return Math.round(value / GRID_SNAP) * GRID_SNAP;
    }

    private boolean wouldCycle(TreeEditorNode child, TreeEditorNode parent) {
        TreeEditorNode current = parent;
        int guard = 0;
        while (current != null && guard++ < 256) {
            if (current == child) {
                return true;
            }
            current = find(current.getParentKey());
        }
        return false;
    }

    private String uniqueKey(String base) {
        String sanitized = sanitizeKey(base);
        if (find(sanitized) == null) {
            return sanitized;
        }
        int index = 2;
        while (find(sanitized + "_" + index) != null) {
            index++;
        }
        return sanitized + "_" + index;
    }

    private static String sanitizeKey(String key) {
        String trimmed = key == null ? "" : key.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
        while (trimmed.contains("__")) {
            trimmed = trimmed.replace("__", "_");
        }
        if (trimmed.startsWith("_")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    @Nullable
    private static String firstParentKey(Ability ability) {
        UnlockingHandler handler = ability.getStateManager().getUnlockingHandler();
        List<AbilityReference> parents = handler.getParentAbilities();
        if (parents == null || parents.isEmpty()) {
            return null;
        }
        return parents.getFirst().abilityKey();
    }

    private static int readCostPoints(Ability ability) {
        UnlockingHandler handler = ability.getStateManager().getUnlockingHandler();
        if (handler instanceof BuyableUnlockingHandler buyable && buyable.cost instanceof UpgradePointCost upgradeCost) {
            return upgradeCost.getPoints();
        }
        return 1;
    }
}
