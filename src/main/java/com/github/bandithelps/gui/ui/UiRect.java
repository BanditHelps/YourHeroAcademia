package com.github.bandithelps.gui.ui;

public record UiRect(int x, int y, int width, int height) {
    public boolean contains(double px, double py) {
        return px >= this.x
                && py >= this.y
                && px <= this.x + this.width
                && py <= this.y + this.height;
    }
}
