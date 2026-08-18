package com.github.bandithelps.gui.tree;

import com.github.bandithelps.utils.tree.ConnectionPathProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.phys.Vec2;
import net.threetag.palladium.power.ability.AbilityProperties;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Intermediate grid-space waypoints between a parent node and this child.
 * Parent and child centers are implicit endpoints and are not stored.
 */
public final class TreeConnectionPath {
    public static final String JSON_KEY = "gui_connection";
    public static final TreeConnectionPath EMPTY = new TreeConnectionPath(List.of());

    private static final Codec<Vec2> POINT_CODEC = Codec.DOUBLE.listOf().comapFlatMap(
            list -> {
                if (list.size() < 2) {
                    return DataResult.error(() -> "gui_connection point must have 2 numbers");
                }
                return DataResult.success(new Vec2(list.get(0).floatValue(), list.get(1).floatValue()));
            },
            vec -> List.of((double) vec.x, (double) vec.y)
    );

    public static final Codec<TreeConnectionPath> CODEC = POINT_CODEC.listOf().xmap(TreeConnectionPath::new, TreeConnectionPath::waypoints);

    private final List<Vec2> waypoints;

    public TreeConnectionPath(List<Vec2> waypoints) {
        if (waypoints == null || waypoints.isEmpty()) {
            this.waypoints = List.of();
            return;
        }
        List<Vec2> copy = new ArrayList<>(waypoints.size());
        for (Vec2 point : waypoints) {
            copy.add(new Vec2(TreeEditorDraft.clean(point.x), TreeEditorDraft.clean(point.y)));
        }
        this.waypoints = List.copyOf(copy);
    }

    public static TreeConnectionPath fromProperties(AbilityProperties properties) {
        return ConnectionPathProperties.of(properties).yha$getGuiConnection();
    }

    public static TreeConnectionPath fromJson(@Nullable JsonElement element) {
        if (element == null || !element.isJsonArray()) {
            return EMPTY;
        }
        List<Vec2> points = new ArrayList<>();
        for (JsonElement entry : element.getAsJsonArray()) {
            if (!entry.isJsonArray()) {
                continue;
            }
            JsonArray pair = entry.getAsJsonArray();
            if (pair.size() < 2 || !pair.get(0).isJsonPrimitive() || !pair.get(1).isJsonPrimitive()) {
                continue;
            }
            points.add(new Vec2(pair.get(0).getAsFloat(), pair.get(1).getAsFloat()));
        }
        return points.isEmpty() ? EMPTY : new TreeConnectionPath(points);
    }

    public boolean isEmpty() {
        return this.waypoints.isEmpty();
    }

    public int size() {
        return this.waypoints.size();
    }

    public List<Vec2> waypoints() {
        return this.waypoints;
    }

    public Vec2 get(int index) {
        return this.waypoints.get(index);
    }

    public TreeConnectionPath copy() {
        return this.isEmpty() ? EMPTY : new TreeConnectionPath(this.waypoints);
    }

    public TreeConnectionPath withReplaced(int index, float x, float y) {
        List<Vec2> next = copyWaypoints();
        next.set(index, new Vec2(x, y));
        return new TreeConnectionPath(next);
    }

    public TreeConnectionPath withInserted(int index, float x, float y) {
        List<Vec2> next = copyWaypoints();
        next.add(Math.max(0, Math.min(index, next.size())), new Vec2(x, y));
        return new TreeConnectionPath(next);
    }

    public TreeConnectionPath withRemoved(int index) {
        List<Vec2> next = copyWaypoints();
        if (index < 0 || index >= next.size()) {
            return this;
        }
        next.remove(index);
        return next.isEmpty() ? EMPTY : new TreeConnectionPath(next);
    }

    public JsonArray toJson() {
        JsonArray array = new JsonArray();
        for (Vec2 point : this.waypoints) {
            JsonArray pair = new JsonArray();
            pair.add(TreeEditorDraft.gridNumber(point.x));
            pair.add(TreeEditorDraft.gridNumber(point.y));
            array.add(pair);
        }
        return array;
    }

    private List<Vec2> copyWaypoints() {
        List<Vec2> next = new ArrayList<>(this.waypoints.size());
        for (Vec2 point : this.waypoints) {
            next.add(new Vec2(point.x, point.y));
        }
        return next;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof TreeConnectionPath path)) {
            return false;
        }
        if (this.waypoints.size() != path.waypoints.size()) {
            return false;
        }
        for (int index = 0; index < this.waypoints.size(); index++) {
            Vec2 left = this.waypoints.get(index);
            Vec2 right = path.waypoints.get(index);
            if (Float.compare(left.x, right.x) != 0 || Float.compare(left.y, right.y) != 0) {
                return false;
            }
        }
        return true;
    }

    @Override
    public int hashCode() {
        int hash = 1;
        for (Vec2 point : this.waypoints) {
            hash = 31 * hash + Float.hashCode(point.x);
            hash = 31 * hash + Float.hashCode(point.y);
        }
        return hash;
    }
}
