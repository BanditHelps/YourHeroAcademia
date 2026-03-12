package com.github.bandithelps.gui.gene.model;

public final class GeneEdge {
    private final String fromNodeId;
    private final String fromPortId;
    private final String toNodeId;
    private final String toPortId;

    public GeneEdge(String fromNodeId, String fromPortId, String toNodeId, String toPortId) {
        this.fromNodeId = fromNodeId;
        this.fromPortId = fromPortId;
        this.toNodeId = toNodeId;
        this.toPortId = toPortId;
    }

    public String fromNodeId() {
        return this.fromNodeId;
    }

    public String fromPortId() {
        return this.fromPortId;
    }

    public String toNodeId() {
        return this.toNodeId;
    }

    public String toPortId() {
        return this.toPortId;
    }
}
