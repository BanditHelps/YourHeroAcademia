package com.github.bandithelps.gui.gene.render;

import com.github.bandithelps.gui.GeneUiStyle;
import com.github.bandithelps.gui.gene.interaction.ConnectionDraft;
import com.github.bandithelps.gui.gene.interaction.GraphViewport;
import com.github.bandithelps.gui.gene.interaction.SelectionState;
import com.github.bandithelps.gui.gene.model.GeneEdge;
import com.github.bandithelps.gui.gene.model.GeneGraphDocument;
import com.github.bandithelps.gui.gene.model.GeneNode;
import com.github.bandithelps.gui.gene.model.GenePort;
import com.github.bandithelps.gui.gene.model.NodeKind;
import com.github.bandithelps.gui.gene.model.PortDirection;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/**
 * Stateless renderer for graph canvas primitives and overlays.
 */
public final class GraphCanvasRenderer {
    private static final int GRID_COLOR = 0x22494949;
    private static final int NODE_BACKGROUND = 0xFF9F9F9F;
    private static final int NODE_BORDER = 0xFF4A4A4A;
    private static final int SELECTED_BORDER = 0xFFEFD67A;
    private static final int PORT_INPUT_COLOR = 0xFF6CA6E8;
    private static final int PORT_OUTPUT_COLOR = 0xFFD48A6A;
    private static final int EDGE_COLOR = 0xFFE7E7E7;
    private static final int EDGE_INVALID_COLOR = 0xFFCF4D4D;
    private static final int MACHINE_FRAME_BG = 0xFF7E7E7E;
    private static final int MACHINE_FRAME_BORDER = 0xFF3C3C3C;

    public void render(
            GuiGraphicsExtractor graphics,
            Font font,
            GeneGraphDocument graph,
            GraphViewport viewport,
            SelectionState selection,
            ConnectionDraft draft,
            int canvasX,
            int canvasY,
            int canvasWidth,
            int canvasHeight
    ) {
        renderGrid(graphics, viewport, canvasX, canvasY, canvasWidth, canvasHeight);
        renderEdges(graphics, graph, viewport, canvasX, canvasY);
        renderNodes(graphics, font, graph, viewport, selection, canvasX, canvasY);
        renderDraftConnection(graphics, graph, viewport, draft, canvasX, canvasY);
        renderMarquee(graphics, viewport, selection, canvasX, canvasY);
    }

    private void renderGrid(GuiGraphicsExtractor graphics, GraphViewport viewport, int canvasX, int canvasY, int canvasWidth, int canvasHeight) {
        int spacing = Math.max(8, (int) Math.round(16 * viewport.zoom()));
        int originX = (int) Math.round(canvasX + viewport.panX());
        int originY = (int) Math.round(canvasY + viewport.panY());

        for (int x = originX % spacing; x < canvasWidth; x += spacing) {
            graphics.verticalLine(canvasX + x, canvasY, canvasY + canvasHeight, GRID_COLOR);
        }
        for (int y = originY % spacing; y < canvasHeight; y += spacing) {
            graphics.horizontalLine(canvasX, canvasX + canvasWidth, canvasY + y, GRID_COLOR);
        }
    }

    private void renderNodes(
            GuiGraphicsExtractor graphics,
            Font font,
            GeneGraphDocument graph,
            GraphViewport viewport,
            SelectionState selection,
            int canvasX,
            int canvasY
    ) {
        for (GeneNode node : graph.nodes()) {
            int x = viewport.worldToScreenX(node.worldX(), canvasX);
            int y = viewport.worldToScreenY(node.worldY(), canvasY);
            int width = (int) Math.round(GeneNode.WIDTH * viewport.zoom());
            int height = (int) Math.round(node.height() * viewport.zoom());
            int headerHeight = (int) Math.round(GeneNode.HEADER_HEIGHT * viewport.zoom());
            int borderColor = selection.selectedNodeIds().contains(node.id()) ? SELECTED_BORDER : NODE_BORDER;

            if (node.kind() == NodeKind.MACHINE) {
                GeneUiStyle.drawBevelPanel(graphics, x, y, width, height, NODE_BACKGROUND);
                graphics.outline(x, y, width, height, borderColor);

                int iconFrameSize = Math.max(20, Math.min(32, (int) Math.round(24 * viewport.zoom())));
                int iconFrameX = x + (width - iconFrameSize) / 2;
                int iconFrameY = y + (height - iconFrameSize) / 2;
                graphics.fill(iconFrameX, iconFrameY, iconFrameX + iconFrameSize, iconFrameY + iconFrameSize, MACHINE_FRAME_BG);
                graphics.outline(iconFrameX, iconFrameY, iconFrameSize, iconFrameSize, MACHINE_FRAME_BORDER);
                graphics.item(machineIconForNode(node), iconFrameX + 3, iconFrameY + 3);
            } else {
                GeneUiStyle.drawBevelPanel(graphics, x, y, width, height, NODE_BACKGROUND);
                graphics.outline(x, y, width, height, borderColor);
                graphics.fill(x, y, x + width, y + headerHeight, node.color());
                int titleMaxWidth = width - 12;
                if (titleMaxWidth > 8) {
                    String renderedTitle = font.plainSubstrByWidth(node.title(), Math.max(8, titleMaxWidth));
                    if (renderedTitle.length() < node.title().length() && renderedTitle.length() > 1) {
                        renderedTitle = renderedTitle.substring(0, renderedTitle.length() - 1) + "...";
                    }
                    int titleY = y + Math.max(2, (headerHeight - font.lineHeight) / 2);
                    graphics.text(font, renderedTitle, x + 6, titleY, 0xFF1E1E1E, false);
                }
            }

            drawPorts(graphics, node, viewport, canvasX, canvasY, node.inputPorts(), PortDirection.INPUT, PORT_INPUT_COLOR);
            drawPorts(graphics, node, viewport, canvasX, canvasY, node.outputPorts(), PortDirection.OUTPUT, PORT_OUTPUT_COLOR);
        }
    }

    private void drawPorts(
            GuiGraphicsExtractor graphics,
            GeneNode node,
            GraphViewport viewport,
            int canvasX,
            int canvasY,
            java.util.List<GenePort> ports,
            PortDirection direction,
            int color
    ) {
        int radius = Math.max(2, (int) Math.round(GraphHitTest.PORT_RADIUS * viewport.zoom()));
        for (int i = 0; i < ports.size(); i++) {
            int worldX = GraphHitTest.portWorldX(node, direction);
            int worldY = GraphHitTest.portWorldY(node, i);
            int sx = viewport.worldToScreenX(worldX, canvasX);
            int sy = viewport.worldToScreenY(worldY, canvasY);
            graphics.fill(sx - radius, sy - radius, sx + radius + 1, sy + radius + 1, color);
        }
    }

    private void renderEdges(GuiGraphicsExtractor graphics, GeneGraphDocument graph, GraphViewport viewport, int canvasX, int canvasY) {
        for (GeneEdge edge : graph.edges()) {
            GeneNode fromNode = graph.nodeById(edge.fromNodeId());
            GeneNode toNode = graph.nodeById(edge.toNodeId());
            if (fromNode == null || toNode == null) {
                continue;
            }

            int fromIndex = indexForPort(fromNode.outputPorts(), edge.fromPortId());
            int toIndex = indexForPort(toNode.inputPorts(), edge.toPortId());
            if (fromIndex < 0 || toIndex < 0) {
                continue;
            }

            int x1 = viewport.worldToScreenX(GraphHitTest.portWorldX(fromNode, PortDirection.OUTPUT), canvasX);
            int y1 = viewport.worldToScreenY(GraphHitTest.portWorldY(fromNode, fromIndex), canvasY);
            int x2 = viewport.worldToScreenX(GraphHitTest.portWorldX(toNode, PortDirection.INPUT), canvasX);
            int y2 = viewport.worldToScreenY(GraphHitTest.portWorldY(toNode, toIndex), canvasY);
            drawLine(graphics, x1, y1, x2, y2, EDGE_COLOR);
        }
    }

    private void renderDraftConnection(
            GuiGraphicsExtractor graphics,
            GeneGraphDocument graph,
            GraphViewport viewport,
            ConnectionDraft draft,
            int canvasX,
            int canvasY
    ) {
        if (!draft.isActive()) {
            return;
        }
        GeneNode node = graph.nodeById(draft.fromNodeId());
        if (node == null) {
            return;
        }

        int portIndex = indexForPort(node.outputPorts(), draft.fromPortId());
        if (portIndex < 0) {
            return;
        }

        int x1 = viewport.worldToScreenX(GraphHitTest.portWorldX(node, PortDirection.OUTPUT), canvasX);
        int y1 = viewport.worldToScreenY(GraphHitTest.portWorldY(node, portIndex), canvasY);
        int x2 = viewport.worldToScreenX(draft.currentWorldX(), canvasX);
        int y2 = viewport.worldToScreenY(draft.currentWorldY(), canvasY);
        drawLine(graphics, x1, y1, x2, y2, draft.validTargetHover() ? EDGE_COLOR : EDGE_INVALID_COLOR);
    }

    private void renderMarquee(GuiGraphicsExtractor graphics, GraphViewport viewport, SelectionState selection, int canvasX, int canvasY) {
        if (!selection.isMarqueeActive()) {
            return;
        }

        int x1 = viewport.worldToScreenX(selection.marqueeStartWorldX(), canvasX);
        int y1 = viewport.worldToScreenY(selection.marqueeStartWorldY(), canvasY);
        int x2 = viewport.worldToScreenX(selection.marqueeEndWorldX(), canvasX);
        int y2 = viewport.worldToScreenY(selection.marqueeEndWorldY(), canvasY);

        int minX = Math.min(x1, x2);
        int minY = Math.min(y1, y2);
        int maxX = Math.max(x1, x2);
        int maxY = Math.max(y1, y2);
        graphics.fill(minX, minY, maxX, maxY, 0x33E0D3A1);
        graphics.outline(minX, minY, maxX - minX, maxY - minY, 0xFFC8B874);
    }

    private void drawLine(GuiGraphicsExtractor graphics, int x1, int y1, int x2, int y2, int color) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        if (steps == 0) {
            graphics.fill(x1, y1, x1 + 1, y1 + 1, color);
            return;
        }

        for (int i = 0; i <= steps; i++) {
            float t = i / (float) steps;
            int px = Math.round(x1 + (x2 - x1) * t);
            int py = Math.round(y1 + (y2 - y1) * t);
            graphics.fill(px, py, px + 2, py + 2, color);
        }
    }

    private int indexForPort(java.util.List<GenePort> ports, String portId) {
        for (int i = 0; i < ports.size(); i++) {
            if (ports.get(i).id().equals(portId)) {
                return i;
            }
        }
        return -1;
    }

    private ItemStack machineIconForNode(GeneNode node) {
        return switch (node.title()) {
            case "Combiner" -> new ItemStack(Blocks.CRAFTING_TABLE);
            case "Sequencer" -> new ItemStack(Blocks.LECTERN);
            case "Stabilizer" -> new ItemStack(Blocks.BEACON);
            case "Extractor" -> new ItemStack(Blocks.BLAST_FURNACE);
            default -> new ItemStack(Blocks.IRON_BLOCK);
        };
    }
}
