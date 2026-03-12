package com.github.bandithelps.gui.gene.render;

import com.github.bandithelps.gui.gene.model.GeneNode;
import com.github.bandithelps.gui.gene.model.GenePort;
import com.github.bandithelps.gui.gene.model.PortDirection;

import java.util.Collection;
import java.util.List;

public final class GraphHitTest {
    public static final int PORT_RADIUS = 4;

    private GraphHitTest() {
    }

    public static GeneNode findNodeAt(Collection<GeneNode> nodes, double worldX, double worldY) {
        GeneNode hit = null;
        for (GeneNode node : nodes) {
            if (isInsideNode(node, worldX, worldY)) {
                hit = node;
            }
        }
        return hit;
    }

    public static PortHit findPortAt(Collection<GeneNode> nodes, double worldX, double worldY) {
        for (GeneNode node : nodes) {
            PortHit inputHit = findPortAt(node, node.inputPorts(), PortDirection.INPUT, worldX, worldY);
            if (inputHit != null) {
                return inputHit;
            }

            PortHit outputHit = findPortAt(node, node.outputPorts(), PortDirection.OUTPUT, worldX, worldY);
            if (outputHit != null) {
                return outputHit;
            }
        }
        return null;
    }

    public static boolean isInsideNode(GeneNode node, double worldX, double worldY) {
        double x1 = node.worldX();
        double y1 = node.worldY();
        double x2 = x1 + GeneNode.WIDTH;
        double y2 = y1 + node.height();
        return worldX >= x1 && worldX <= x2 && worldY >= y1 && worldY <= y2;
    }

    public static int portWorldX(GeneNode node, PortDirection direction) {
        return direction == PortDirection.INPUT
                ? (int) Math.round(node.worldX())
                : (int) Math.round(node.worldX() + GeneNode.WIDTH);
    }

    public static int portWorldY(GeneNode node, int index) {
        return (int) Math.round(node.worldY() + GeneNode.HEADER_HEIGHT + 10 + index * GeneNode.PORT_SPACING);
    }

    private static PortHit findPortAt(GeneNode node, List<GenePort> ports, PortDirection direction, double worldX, double worldY) {
        for (int i = 0; i < ports.size(); i++) {
            GenePort port = ports.get(i);
            int px = portWorldX(node, direction);
            int py = portWorldY(node, i);
            double dx = worldX - px;
            double dy = worldY - py;
            if (dx * dx + dy * dy <= PORT_RADIUS * PORT_RADIUS) {
                return new PortHit(node.id(), port.id(), direction);
            }
        }
        return null;
    }

    public record PortHit(String nodeId, String portId, PortDirection direction) {
    }
}
