package com.github.bandithelps.gui.gene.interaction;

import com.github.bandithelps.gui.gene.model.GeneEdge;
import com.github.bandithelps.gui.gene.model.GeneGraphDocument;
import com.github.bandithelps.gui.gene.model.GeneNode;
import com.github.bandithelps.gui.gene.model.PortDirection;
import com.github.bandithelps.gui.gene.render.GraphHitTest;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Central interaction state machine for graph editing.
 */
public final class GraphInputController {
    private final GeneGraphDocument graph;
    private final GraphViewport viewport;
    private final SelectionState selection;
    private final ConnectionDraft connectionDraft;
    private final Map<String, NodeStartPosition> dragStartByNodeId = new LinkedHashMap<>();
    private boolean draggingNodes;
    private boolean panning;
    private double dragStartWorldX;
    private double dragStartWorldY;

    public GraphInputController(
            GeneGraphDocument graph,
            GraphViewport viewport,
            SelectionState selection,
            ConnectionDraft connectionDraft
    ) {
        this.graph = graph;
        this.viewport = viewport;
        this.selection = selection;
        this.connectionDraft = connectionDraft;
    }

    /**
     * Handles click begin for selection, connection draft, marquee, and panning.
     */
    public boolean onMouseClicked(
            int button,
            double mouseX,
            double mouseY,
            int canvasX,
            int canvasY,
            boolean shiftDown
    ) {
        double worldX = this.viewport.screenToWorldX(mouseX, canvasX);
        double worldY = this.viewport.screenToWorldY(mouseY, canvasY);

        if (button == 1) {
            this.panning = true;
            return true;
        }

        if (button != 0) {
            return false;
        }

        GraphHitTest.PortHit portHit = GraphHitTest.findPortAt(this.graph.nodes(), worldX, worldY);
        if (portHit != null && portHit.direction() == PortDirection.OUTPUT) {
            this.connectionDraft.start(portHit.nodeId(), portHit.portId(), worldX, worldY);
            return true;
        }

        GeneNode nodeHit = GraphHitTest.findNodeAt(this.graph.nodes(), worldX, worldY);
        if (nodeHit != null) {
            if (shiftDown) {
                this.selection.toggleSelection(nodeHit.id());
            } else if (!this.selection.selectedNodeIds().contains(nodeHit.id())) {
                this.selection.setSingleSelection(nodeHit.id());
            }

            this.dragStartByNodeId.clear();
            for (String nodeId : this.selection.selectedNodeIds()) {
                GeneNode selectedNode = this.graph.nodeById(nodeId);
                if (selectedNode == null) {
                    continue;
                }
                this.dragStartByNodeId.put(nodeId, new NodeStartPosition(selectedNode.worldX(), selectedNode.worldY()));
            }
            this.draggingNodes = true;
            this.dragStartWorldX = worldX;
            this.dragStartWorldY = worldY;
            return true;
        }

        if (!shiftDown) {
            this.selection.clearSelection();
        }
        this.selection.startMarquee(worldX, worldY);
        return true;
    }

    /**
     * Handles continuous drag updates for camera panning, node movement, and marquee/connection previews.
     */
    public boolean onMouseDragged(
            int button,
            double mouseX,
            double mouseY,
            double dragX,
            double dragY,
            int canvasX,
            int canvasY
    ) {
        double worldX = this.viewport.screenToWorldX(mouseX, canvasX);
        double worldY = this.viewport.screenToWorldY(mouseY, canvasY);

        if (button == 1 && this.panning) {
            this.viewport.panBy(dragX, dragY);
            return true;
        }

        if (button != 0) {
            return false;
        }

        if (this.connectionDraft.isActive()) {
            this.connectionDraft.updateCursor(worldX, worldY);
            GraphHitTest.PortHit target = GraphHitTest.findPortAt(this.graph.nodes(), worldX, worldY);
            this.connectionDraft.setValidTargetHover(isValidConnectionTarget(target));
            return true;
        }

        if (this.draggingNodes) {
            double deltaX = worldX - this.dragStartWorldX;
            double deltaY = worldY - this.dragStartWorldY;
            for (Map.Entry<String, NodeStartPosition> entry : this.dragStartByNodeId.entrySet()) {
                GeneNode node = this.graph.nodeById(entry.getKey());
                if (node == null) {
                    continue;
                }
                NodeStartPosition start = entry.getValue();
                node.setWorldPosition(start.worldX() + deltaX, start.worldY() + deltaY);
            }
            return true;
        }

        if (this.selection.isMarqueeActive()) {
            this.selection.updateMarquee(worldX, worldY);
            return true;
        }
        return false;
    }

    /**
     * Handles drag finalization and commits marquee selection or edge creation.
     */
    public boolean onMouseReleased(
            int button,
            double mouseX,
            double mouseY,
            int canvasX,
            int canvasY,
            boolean shiftDown
    ) {
        double worldX = this.viewport.screenToWorldX(mouseX, canvasX);
        double worldY = this.viewport.screenToWorldY(mouseY, canvasY);

        if (button == 1) {
            this.panning = false;
            return true;
        }

        if (button != 0) {
            return false;
        }

        if (this.connectionDraft.isActive()) {
            GraphHitTest.PortHit target = GraphHitTest.findPortAt(this.graph.nodes(), worldX, worldY);
            if (isValidConnectionTarget(target)) {
                this.graph.addEdge(new GeneEdge(
                        this.connectionDraft.fromNodeId(),
                        this.connectionDraft.fromPortId(),
                        target.nodeId(),
                        target.portId()
                ));
            }
            this.connectionDraft.clear();
            return true;
        }

        this.draggingNodes = false;
        this.dragStartByNodeId.clear();

        if (this.selection.isMarqueeActive()) {
            if (!shiftDown) {
                this.selection.clearSelection();
            }

            double minX = Math.min(this.selection.marqueeStartWorldX(), this.selection.marqueeEndWorldX());
            double minY = Math.min(this.selection.marqueeStartWorldY(), this.selection.marqueeEndWorldY());
            double maxX = Math.max(this.selection.marqueeStartWorldX(), this.selection.marqueeEndWorldX());
            double maxY = Math.max(this.selection.marqueeStartWorldY(), this.selection.marqueeEndWorldY());

            for (GeneNode node : this.graph.nodes()) {
                double nodeMinX = node.worldX();
                double nodeMinY = node.worldY();
                double nodeMaxX = node.worldX() + GeneNode.WIDTH;
                double nodeMaxY = node.worldY() + node.height();
                if (nodeMaxX < minX || nodeMinX > maxX || nodeMaxY < minY || nodeMinY > maxY) {
                    continue;
                }
                this.selection.selectedNodeIds().add(node.id());
            }
            this.selection.stopMarquee();
            return true;
        }

        return false;
    }

    private boolean isValidConnectionTarget(GraphHitTest.PortHit target) {
        if (target == null || target.direction() != PortDirection.INPUT) {
            return false;
        }
        if (target.nodeId().equals(this.connectionDraft.fromNodeId())) {
            return false;
        }
        return !this.graph.edgeExists(this.connectionDraft.fromPortId(), target.portId());
    }

    private record NodeStartPosition(double worldX, double worldY) {
    }
}
