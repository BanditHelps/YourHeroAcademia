package com.github.bandithelps.gui.tree;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Draws power-tree connections with Palladium's 3px outline / 1px fill, including diagonal segments.
 */
public final class TreeConnectionRenderer {
    public static final int GRID_SIZE = 50;
    public static final int HIT_THRESHOLD = 5;
    public static final int HANDLE_HIT = 5;

    private TreeConnectionRenderer() {
    }

    public record Pixel(int x, int y) {
    }

    public record SegmentHit(int segmentIndex, double distance) {
    }

    public static void drawPolyline(GuiGraphicsExtractor graphics, List<Pixel> points, boolean outline, int color) {
        if (points == null || points.size() < 2) {
            return;
        }
        for (int index = 0; index < points.size() - 1; index++) {
            Pixel start = points.get(index);
            Pixel end = points.get(index + 1);
            drawSegment(graphics, start.x, start.y, end.x, end.y, outline, color);
        }
    }

    public static void drawBus(
            GuiGraphicsExtractor graphics,
            int parentX,
            int parentY,
            List<Integer> childXs,
            List<Integer> childYs,
            boolean outline,
            int color
    ) {
        if (childXs.isEmpty() || childXs.size() != childYs.size()) {
            return;
        }
        int busX = parentX + (childXs.getFirst() - parentX) / 2;
        int minY = parentY;
        int maxY = parentY;
        for (int childY : childYs) {
            minY = Math.min(minY, childY);
            maxY = Math.max(maxY, childY);
        }
        drawVLine(graphics, busX, minY, maxY, outline, color);
        drawHLine(graphics, parentX, busX, parentY, outline, color);
        for (int index = 0; index < childXs.size(); index++) {
            drawHLine(graphics, busX, childXs.get(index), childYs.get(index), outline, color);
        }
    }

    public static void drawHandle(GuiGraphicsExtractor graphics, int x, int y, boolean hovered) {
        int fill = hovered ? 0xFFFFFF66 : 0xFF00FFFF;
        graphics.fill(x - 3, y - 3, x + 4, y + 4, 0xFF000000);
        graphics.fill(x - 2, y - 2, x + 3, y + 3, fill);
    }

    public static void drawSegment(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, boolean outline, int color) {
        if (y1 == y2) {
            drawHLine(graphics, x1, x2, y1, outline, color);
            return;
        }
        if (x1 == x2) {
            drawVLine(graphics, x1, y1, y2, outline, color);
            return;
        }
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            stamp(graphics, x1, y1, outline, color);
            return;
        }
        float x = x1;
        float y = y1;
        float xStep = (x2 - x1) / (float) steps;
        float yStep = (y2 - y1) / (float) steps;
        for (int index = 0; index <= steps; index++) {
            stamp(graphics, Math.round(x), Math.round(y), outline, color);
            x += xStep;
            y += yStep;
        }
    }

    public static void drawHLine(GuiGraphicsExtractor graphics, int x1, int x2, int y, boolean outline, int color) {
        int min = Math.min(x1, x2);
        int max = Math.max(x1, x2);
        if (outline) {
            graphics.fill(min - 2, y - 2, max + 1, y + 1, color);
        } else {
            graphics.fill(min - 1, y - 1, max, y, color);
        }
    }

    public static void drawVLine(GuiGraphicsExtractor graphics, int x, int y1, int y2, boolean outline, int color) {
        int min = Math.min(y1, y2);
        int max = Math.max(y1, y2);
        if (outline) {
            graphics.fill(x - 2, min - 2, x + 1, max + 1, color);
        } else {
            graphics.fill(x - 1, min - 1, x, max, color);
        }
    }

    @Nullable
    public static SegmentHit hitTest(List<Pixel> points, int mouseX, int mouseY, double threshold) {
        if (points == null || points.size() < 2) {
            return null;
        }
        int bestIndex = -1;
        double bestDistance = threshold;
        for (int index = 0; index < points.size() - 1; index++) {
            Pixel start = points.get(index);
            Pixel end = points.get(index + 1);
            double distance = distanceToSegment(mouseX, mouseY, start.x, start.y, end.x, end.y);
            if (distance <= bestDistance) {
                bestDistance = distance;
                bestIndex = index;
            }
        }
        return bestIndex < 0 ? null : new SegmentHit(bestIndex, bestDistance);
    }

    public static boolean hitsHandle(int mouseX, int mouseY, int x, int y) {
        return Math.abs(mouseX - x) <= HANDLE_HIT && Math.abs(mouseY - y) <= HANDLE_HIT;
    }

    public static int palladiumPixel(float grid) {
        return Math.round(grid * GRID_SIZE + GRID_SIZE / 2.0F);
    }

    public static double distanceToSegment(int px, int py, int x1, int y1, int x2, int y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        if (dx == 0 && dy == 0) {
            return Math.hypot(px - x1, py - y1);
        }
        double lengthSquared = dx * dx + dy * dy;
        double t = ((px - x1) * dx + (py - y1) * dy) / lengthSquared;
        t = Math.max(0.0, Math.min(1.0, t));
        double closestX = x1 + t * dx;
        double closestY = y1 + t * dy;
        return Math.hypot(px - closestX, py - closestY);
    }

    private static void stamp(GuiGraphicsExtractor graphics, int x, int y, boolean outline, int color) {
        if (outline) {
            graphics.fill(x - 2, y - 2, x + 1, y + 1, color);
        } else {
            graphics.fill(x - 1, y - 1, x, y, color);
        }
    }
}
