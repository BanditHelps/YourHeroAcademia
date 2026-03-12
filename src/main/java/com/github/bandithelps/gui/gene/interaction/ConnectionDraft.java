package com.github.bandithelps.gui.gene.interaction;

public final class ConnectionDraft {
    private String fromNodeId;
    private String fromPortId;
    private double currentWorldX;
    private double currentWorldY;
    private boolean active;
    private boolean validTargetHover;

    public boolean isActive() {
        return this.active;
    }

    public String fromNodeId() {
        return this.fromNodeId;
    }

    public String fromPortId() {
        return this.fromPortId;
    }

    public double currentWorldX() {
        return this.currentWorldX;
    }

    public double currentWorldY() {
        return this.currentWorldY;
    }

    public boolean validTargetHover() {
        return this.validTargetHover;
    }

    public void start(String fromNodeId, String fromPortId, double currentWorldX, double currentWorldY) {
        this.fromNodeId = fromNodeId;
        this.fromPortId = fromPortId;
        this.currentWorldX = currentWorldX;
        this.currentWorldY = currentWorldY;
        this.active = true;
        this.validTargetHover = false;
    }

    public void updateCursor(double currentWorldX, double currentWorldY) {
        this.currentWorldX = currentWorldX;
        this.currentWorldY = currentWorldY;
    }

    public void setValidTargetHover(boolean validTargetHover) {
        this.validTargetHover = validTargetHover;
    }

    public void clear() {
        this.active = false;
        this.fromNodeId = null;
        this.fromPortId = null;
        this.validTargetHover = false;
    }
}
