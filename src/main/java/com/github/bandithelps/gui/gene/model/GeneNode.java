package com.github.bandithelps.gui.gene.model;

import java.util.ArrayList;
import java.util.List;

public final class GeneNode {
    public static final int WIDTH = 124;
    public static final int HEADER_HEIGHT = 18;
    public static final int PORT_SPACING = 14;
    public static final int BOTTOM_PADDING = 10;

    private final String id;
    private final NodeKind kind;
    private final String title;
    private final int color;
    private double worldX;
    private double worldY;
    private final List<GenePort> inputPorts;
    private final List<GenePort> outputPorts;

    public GeneNode(
            String id,
            NodeKind kind,
            String title,
            int color,
            double worldX,
            double worldY,
            List<GenePort> inputPorts,
            List<GenePort> outputPorts
    ) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.color = color;
        this.worldX = worldX;
        this.worldY = worldY;
        this.inputPorts = new ArrayList<>(inputPorts);
        this.outputPorts = new ArrayList<>(outputPorts);
    }

    public String id() {
        return this.id;
    }

    public NodeKind kind() {
        return this.kind;
    }

    public String title() {
        return this.title;
    }

    public int color() {
        return this.color;
    }

    public double worldX() {
        return this.worldX;
    }

    public double worldY() {
        return this.worldY;
    }

    public void setWorldPosition(double worldX, double worldY) {
        this.worldX = worldX;
        this.worldY = worldY;
    }

    public List<GenePort> inputPorts() {
        return this.inputPorts;
    }

    public List<GenePort> outputPorts() {
        return this.outputPorts;
    }

    public int height() {
        int rows = Math.max(this.inputPorts.size(), this.outputPorts.size());
        return HEADER_HEIGHT + rows * PORT_SPACING + BOTTOM_PADDING;
    }
}
