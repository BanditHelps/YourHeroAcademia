package com.github.bandithelps.gui.gene.interaction;

import java.util.LinkedHashSet;
import java.util.Set;

public final class SelectionState {
    private final Set<String> selectedNodeIds = new LinkedHashSet<>();
    private boolean marqueeActive;
    private double marqueeStartWorldX;
    private double marqueeStartWorldY;
    private double marqueeEndWorldX;
    private double marqueeEndWorldY;

    public Set<String> selectedNodeIds() {
        return this.selectedNodeIds;
    }

    public void clearSelection() {
        this.selectedNodeIds.clear();
    }

    public void setSingleSelection(String nodeId) {
        this.selectedNodeIds.clear();
        this.selectedNodeIds.add(nodeId);
    }

    public void toggleSelection(String nodeId) {
        if (this.selectedNodeIds.contains(nodeId)) {
            this.selectedNodeIds.remove(nodeId);
            return;
        }
        this.selectedNodeIds.add(nodeId);
    }

    public boolean isMarqueeActive() {
        return this.marqueeActive;
    }

    public double marqueeStartWorldX() {
        return this.marqueeStartWorldX;
    }

    public double marqueeStartWorldY() {
        return this.marqueeStartWorldY;
    }

    public double marqueeEndWorldX() {
        return this.marqueeEndWorldX;
    }

    public double marqueeEndWorldY() {
        return this.marqueeEndWorldY;
    }

    public void startMarquee(double worldX, double worldY) {
        this.marqueeActive = true;
        this.marqueeStartWorldX = worldX;
        this.marqueeStartWorldY = worldY;
        this.marqueeEndWorldX = worldX;
        this.marqueeEndWorldY = worldY;
    }

    public void updateMarquee(double worldX, double worldY) {
        this.marqueeEndWorldX = worldX;
        this.marqueeEndWorldY = worldY;
    }

    public void stopMarquee() {
        this.marqueeActive = false;
    }
}
