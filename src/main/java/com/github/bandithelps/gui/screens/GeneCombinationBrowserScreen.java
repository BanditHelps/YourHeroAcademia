package com.github.bandithelps.gui.screens;

import com.github.bandithelps.client.gene_combiner.ClientGeneCombinationBrowserState;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GeneCombinationBrowserScreen extends Screen {
    private int panelX;
    private int panelY;
    private int scrollOffset;
    private static final int VISIBLE_LINES = 12;
    private final java.util.ArrayList<Button> lineButtons = new java.util.ArrayList<>();
    private Button prevButton;
    private Button nextButton;

    public GeneCombinationBrowserScreen() {
        super(Component.literal("Gene Recipe Browser"));
    }

    @Override
    protected void init() {
        super.init();
        this.panelX = this.width / 2 - 160;
        this.panelY = this.height / 2 - 100;
        this.lineButtons.clear();
        this.addRenderableWidget(Button.builder(Component.literal("Close"), btn -> onClose())
                .bounds(this.panelX + 268, this.panelY + 8, 48, 20)
                .build());
        this.prevButton = this.addRenderableWidget(Button.builder(Component.literal("Prev"), btn -> {
                    this.scrollOffset = Math.max(0, this.scrollOffset - 1);
                    refreshLineButtons();
                }).bounds(this.panelX + 8, this.panelY + 8, 48, 20)
                .build());
        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal("Next"), btn -> {
                    this.scrollOffset = this.scrollOffset + 1;
                    refreshLineButtons();
                }).bounds(this.panelX + 60, this.panelY + 8, 48, 20)
                .build());

        for (int i = 0; i < VISIBLE_LINES; i++) {
            int y = this.panelY + 34 + (i * 13);
            Button lineButton = this.addRenderableWidget(Button.builder(Component.literal(""), btn -> {
            }).bounds(this.panelX + 8, y, 304, 12).build());
            this.lineButtons.add(lineButton);
        }
        refreshLineButtons();
    }

    private void refreshLineButtons() {
        List<String> lines = ClientGeneCombinationBrowserState.getLines();
        int maxOffset = Math.max(0, lines.size() - VISIBLE_LINES);
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset, maxOffset));

        for (int i = 0; i < this.lineButtons.size(); i++) {
            int index = this.scrollOffset + i;
            Button lineButton = this.lineButtons.get(i);
            if (index < lines.size()) {
                String value = lines.get(index);
                if (value.length() > 60) {
                    value = value.substring(0, 60);
                }
                lineButton.setMessage(Component.literal(value));
                lineButton.visible = true;
            } else {
                lineButton.setMessage(Component.literal(""));
                lineButton.visible = false;
            }
            lineButton.active = false;
        }
        this.prevButton.active = this.scrollOffset > 0;
        this.nextButton.active = this.scrollOffset < maxOffset;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
