package com.github.bandithelps.gui.gene.model;

public final class GenePort {
    private final String id;
    private final String nodeId;
    private final PortDirection direction;
    private final String type;
    private final String label;

    public GenePort(String id, String nodeId, PortDirection direction, String type, String label) {
        this.id = id;
        this.nodeId = nodeId;
        this.direction = direction;
        this.type = type;
        this.label = label;
    }

    public String id() {
        return this.id;
    }

    public String nodeId() {
        return this.nodeId;
    }

    public PortDirection direction() {
        return this.direction;
    }

    public String type() {
        return this.type;
    }

    public String label() {
        return this.label;
    }
}
