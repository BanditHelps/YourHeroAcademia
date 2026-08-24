package com.github.bandithelps.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public abstract class TreeEditorPopupScreen extends Screen {
    protected static final int TEXT_LIGHT = TreeEditorTheme.TEXT;
    protected static final int FIELD_H = TreeEditorTheme.FIELD_H;
    protected static final int ROW = TreeEditorTheme.ROW;
    protected static final int HEADER = 26;
    protected static final int FOOTER = 34;
    protected static final int MARGIN = 12;

    protected final Screen parentScreen;
    protected final List<FormRow> rows = new ArrayList<>();
    protected int panelX;
    protected int panelY;
    protected int panelW;
    protected int panelH;
    protected int scroll;
    protected int contentHeight;
    protected String error = "";

    protected TreeEditorPopupScreen(Screen parentScreen, String title) {
        super(Component.literal(title));
        this.parentScreen = parentScreen;
    }

    protected void layoutPanel(int width) {
        this.panelW = Math.min(width, Math.max(200, this.width - MARGIN * 2));
        int needed = HEADER + Math.max(this.contentHeight, 40) + FOOTER;
        this.panelH = Math.min(needed, Math.max(HEADER + 80 + FOOTER, this.height - MARGIN * 2));
        this.panelX = Math.max(0, (this.width - this.panelW) / 2);
        this.panelY = Math.max(0, (this.height - this.panelH) / 2);
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
    }

    protected void addRow(String label, AbstractWidget widget) {
        this.addRow(label, widget, 0, widget.getWidth(), FIELD_H);
    }

    protected void addRow(String label, AbstractWidget widget, int xOffset, int width, int height) {
        this.rows.add(new FormRow(label, widget, xOffset, width, height, null, 0, 0));
    }

    protected void addSplitRow(String label, AbstractWidget left, int leftWidth, AbstractWidget right, int rightWidth) {
        this.rows.add(new FormRow(label, left, 0, leftWidth, FIELD_H, right, leftWidth + 8, rightWidth));
    }

    protected void finishRows() {
        this.contentHeight = this.rows.size() * ROW + 8;
        this.layoutPanel(this.panelW == 0 ? 400 : this.panelW);
        this.applyScroll();
    }

    protected void applyScroll() {
        this.scroll = Math.max(0, Math.min(this.scroll, this.maxScroll()));
        int top = this.contentTop();
        int bottom = this.contentBottom();
        int relY = 14;
        for (FormRow row : this.rows) {
            int x = this.panelX + 10 + row.xOffset;
            int y = top + relY - this.scroll;
            row.widget.setRectangle(row.width, row.height, x, y);
            boolean inView = y + row.height > top && y < bottom;
            row.widget.visible = inView;
            row.widget.active = inView;
            if (!inView && row.widget.isFocused()) {
                row.widget.setFocused(false);
            }
            if (row.trailing != null) {
                row.trailing.setRectangle(row.trailingW, row.height, this.panelX + 10 + row.trailingX, y);
                row.trailing.visible = inView;
                row.trailing.active = inView;
                if (!inView && row.trailing.isFocused()) {
                    row.trailing.setFocused(false);
                }
            }
            relY += ROW;
        }
    }

    protected void drawChrome(GuiGraphicsExtractor graphics, String title) {
        if (this.parentScreen instanceof TreeEditorScreen tree) {
            tree.extractRenderState(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, 0);
        } else {
            this.parentScreen.extractRenderState(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, 0);
        }
        graphics.fill(0, 0, this.width, this.height, TreeEditorTheme.OVERLAY);
        TreeEditorTheme.dialog(graphics, this.font, this.panelX, this.panelY, this.panelW, this.panelH, title);

        int clipTop = this.contentTop();
        int clipBottom = this.contentBottom();
        int x = this.panelX;
        graphics.enableScissor(x + 4, clipTop, x + this.panelW - 4, clipBottom);
        int relY = 4;
        for (FormRow row : this.rows) {
            int labelY = clipTop + relY - this.scroll;
            if (row.trailing != null) {
                int cardY = labelY + 8;
                TreeEditorTheme.fill(graphics, x + 8, cardY - 2, this.panelW - 20, FIELD_H + 4, TreeEditorTheme.PANEL_ALT);
                TreeEditorTheme.fill(graphics, x + 8, cardY - 2, 2, FIELD_H + 4, TreeEditorTheme.ACCENT);
            }
            if (labelY + 8 > clipTop && labelY < clipBottom && row.label != null && !row.label.isBlank()) {
                graphics.text(this.font, row.label, x + 12, labelY, TreeEditorTheme.TEXT_MUTED, false);
            }
            relY += ROW;
        }
        graphics.disableScissor();
        this.drawScrollbar(graphics);
        if (!this.error.isEmpty()) {
            graphics.text(this.font, this.error, x + 12, this.panelY + this.panelH - 22, TreeEditorTheme.DANGER, false);
        }
    }

    protected void drawScrollbar(GuiGraphicsExtractor graphics) {
        int max = this.maxScroll();
        if (max <= 0) {
            return;
        }
        int trackX = this.panelX + this.panelW - 8;
        int trackY = this.contentTop();
        int trackH = this.contentBottom() - trackY;
        TreeEditorTheme.scrollbar(graphics, trackX, trackY, trackH, this.contentHeight, this.scroll, max);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        super.mouseClicked(event, doubleClick);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (this.isInPanel((int) mouseX, (int) mouseY) && this.maxScroll() > 0) {
            this.scroll = Math.max(0, Math.min(this.maxScroll(), this.scroll - (int) Math.signum(scrollY) * ROW));
            this.applyScroll();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parentScreen);
        }
    }

    protected int contentTop() {
        return this.panelY + HEADER;
    }

    protected int contentBottom() {
        return this.panelY + this.panelH - FOOTER;
    }

    protected int maxScroll() {
        return Math.max(0, this.contentHeight - (this.contentBottom() - this.contentTop()));
    }

    protected int fieldW() {
        return this.panelW - 26;
    }

    protected TreeEditorFlatButton addFooterButton(String label, int x, Runnable onPress) {
        return this.addRenderableWidget(new TreeEditorFlatButton(x, this.panelY + this.panelH - 28, 90, 22, label, onPress));
    }

    protected boolean isInPanel(int mouseX, int mouseY) {
        return mouseX >= this.panelX && mouseX < this.panelX + this.panelW
                && mouseY >= this.panelY && mouseY < this.panelY + this.panelH;
    }

    protected record FormRow(
            String label,
            AbstractWidget widget,
            int xOffset,
            int width,
            int height,
            @Nullable AbstractWidget trailing,
            int trailingX,
            int trailingW
    ) {
    }
}
