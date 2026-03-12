package com.github.bandithelps.gui.gene.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mutable graph state for the client-side experimentation editor.
 */
public final class GeneGraphDocument {
    private final Map<String, GeneNode> nodes = new LinkedHashMap<>();
    private final List<GeneEdge> edges = new ArrayList<>();
    private int nodeCounter = 1;

    public Collection<GeneNode> nodes() {
        return this.nodes.values();
    }

    public List<GeneEdge> edges() {
        return this.edges;
    }

    public GeneNode nodeById(String nodeId) {
        return this.nodes.get(nodeId);
    }

    public void addNode(GeneNode node) {
        this.nodes.put(node.id(), node);
    }

    public void addEdge(GeneEdge edge) {
        if (this.edgeExists(edge.fromPortId(), edge.toPortId())) {
            return;
        }
        this.edges.add(edge);
    }

    public boolean edgeExists(String fromPortId, String toPortId) {
        for (GeneEdge edge : this.edges) {
            if (edge.fromPortId().equals(fromPortId) && edge.toPortId().equals(toPortId)) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        this.nodes.clear();
        this.edges.clear();
    }

    public void removeNodes(Set<String> nodeIds) {
        this.nodes.keySet().removeIf(nodeIds::contains);
        this.edges.removeIf(edge -> nodeIds.contains(edge.fromNodeId()) || nodeIds.contains(edge.toNodeId()));
    }

    public String nextNodeId(NodeKind kind) {
        String prefix = kind == NodeKind.GENE ? "gene" : "machine";
        return prefix + "_" + this.nodeCounter++;
    }
}
