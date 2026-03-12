package com.github.bandithelps.gui.gene.interaction;

/**
 * Camera transform between world graph coordinates and screen coordinates.
 */
public final class GraphViewport {
    private double panX;
    private double panY;
    private double zoom;
    private final double minZoom;
    private final double maxZoom;

    public GraphViewport() {
        this.zoom = 1.0D;
        this.minZoom = 0.5D;
        this.maxZoom = 2.5D;
    }

    public double panX() {
        return this.panX;
    }

    public double panY() {
        return this.panY;
    }

    public double zoom() {
        return this.zoom;
    }

    public int worldToScreenX(double worldX, int canvasX) {
        return (int) Math.round(canvasX + this.panX + (worldX * this.zoom));
    }

    public int worldToScreenY(double worldY, int canvasY) {
        return (int) Math.round(canvasY + this.panY + (worldY * this.zoom));
    }

    public double screenToWorldX(double screenX, int canvasX) {
        return (screenX - canvasX - this.panX) / this.zoom;
    }

    public double screenToWorldY(double screenY, int canvasY) {
        return (screenY - canvasY - this.panY) / this.zoom;
    }

    public void panBy(double screenDx, double screenDy) {
        this.panX += screenDx;
        this.panY += screenDy;
    }

    public void resetZoom() {
        this.zoom = 1.0D;
    }

    public void setPan(double panX, double panY) {
        this.panX = panX;
        this.panY = panY;
    }

    public void centerOnWorld(double worldX, double worldY, int canvasX, int canvasY, int canvasWidth, int canvasHeight) {
        this.panX = canvasWidth / 2.0D - (worldX * this.zoom);
        this.panY = canvasHeight / 2.0D - (worldY * this.zoom);
    }

    public void zoomAt(double wheelDelta, double mouseX, double mouseY, int canvasX, int canvasY) {
        double factor = wheelDelta > 0 ? 1.10D : 0.90D;
        double currentWorldX = this.screenToWorldX(mouseX, canvasX);
        double currentWorldY = this.screenToWorldY(mouseY, canvasY);
        this.zoom = clamp(this.zoom * factor, this.minZoom, this.maxZoom);
        this.panX = mouseX - canvasX - currentWorldX * this.zoom;
        this.panY = mouseY - canvasY - currentWorldY * this.zoom;
    }

    private static double clamp(double value, double min, double max) {
        if (value < min) {
            return min;
        }
        if (value > max) {
            return max;
        }
        return value;
    }
}
