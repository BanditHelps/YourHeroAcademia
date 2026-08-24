package com.github.bandithelps.gui.screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class TreeEditorTextEditScreen extends Screen {
    private static final int LINE = 12;

    private final Screen parent;
    private final String heading;
    private final Consumer<String> onSave;
    private final int maxLength;
    private final StringBuilder text;
    private int caret;
    private int selectionAnchor = -1;
    private boolean draggingSelect;
    private int scroll;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    public TreeEditorTextEditScreen(Screen parent, String heading, String value, int maxLength, Consumer<String> onSave) {
        super(Component.literal(heading));
        this.parent = parent;
        this.heading = heading;
        this.onSave = onSave;
        this.maxLength = Math.max(1, maxLength);
        this.text = new StringBuilder(value == null ? "" : value);
        this.caret = this.text.length();
    }

    @Override
    protected void init() {
        super.init();
        this.panelW = Math.min(520, Math.max(280, this.width - 24));
        this.panelH = Math.min(320, Math.max(180, this.height - 24));
        this.panelX = Math.max(0, (this.width - this.panelW) / 2);
        this.panelY = Math.max(0, (this.height - this.panelH) / 2);
        this.addRenderableWidget(new TreeEditorFlatButton(this.panelX + 12, this.panelY + this.panelH - 30, 90, 22, "Save", this::save));
        this.addRenderableWidget(new TreeEditorFlatButton(this.panelX + this.panelW - 102, this.panelY + this.panelH - 30, 90, 22, "Cancel", this::onClose));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        this.parent.extractRenderState(graphics, Integer.MIN_VALUE, Integer.MIN_VALUE, partialTick);
        graphics.fill(0, 0, this.width, this.height, TreeEditorTheme.OVERLAY);
        TreeEditorTheme.dialog(graphics, this.font, this.panelX, this.panelY, this.panelW, this.panelH, this.heading);
        graphics.text(this.font, "Ctrl+A selects all. Ctrl+Backspace deletes a word.", this.panelX + 12, this.panelY + 28, TreeEditorTheme.TEXT_MUTED, false);

        int left = this.panelX + 10;
        int top = this.panelY + 42;
        int right = this.panelX + this.panelW - 10;
        int bottom = this.panelY + this.panelH - 38;
        TreeEditorTheme.rect(graphics, left, top, right - left, bottom - top, TreeEditorTheme.INPUT, TreeEditorTheme.BORDER);

        List<VisualLine> lines = this.visualLines(right - left - 10);
        int visible = Math.max(1, (bottom - top - 8) / LINE);
        this.scroll = Math.max(0, Math.min(this.scroll, Math.max(0, lines.size() - visible)));
        this.ensureCaretVisible(lines, visible);

        int selStart = this.hasSelection() ? this.selectionStart() : -1;
        int selEnd = this.hasSelection() ? this.selectionEnd() : -1;

        graphics.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
        int y = top + 5 - this.scroll * LINE;
        for (int index = 0; index < lines.size(); index++) {
            VisualLine line = lines.get(index);
            if (y + LINE >= top && y <= bottom) {
                this.drawSelection(graphics, line, left, y, selStart, selEnd);
                graphics.text(this.font, line.text, left + 6, y, TreeEditorTheme.TEXT, false);
                if (this.caret >= line.start && this.caret <= line.end) {
                    int caretX = left + 6 + this.font.width(this.text.substring(line.start, this.caret));
                    graphics.fill(caretX, y - 1, caretX + 1, y + 9, TreeEditorTheme.ACCENT);
                }
            }
            y += LINE;
        }
        graphics.disableScissor();
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        int mouseX = (int) event.x();
        int mouseY = (int) event.y();
        int left = this.panelX + 10;
        int top = this.panelY + 42;
        int right = this.panelX + this.panelW - 10;
        int bottom = this.panelY + this.panelH - 38;
        if (mouseX < left || mouseX >= right || mouseY < top || mouseY >= bottom) {
            return true;
        }
        int pos = this.indexAt(mouseX, mouseY);
        if (event.hasShiftDown()) {
            this.moveCaretTo(pos, true);
        } else {
            this.caret = pos;
            this.selectionAnchor = pos;
            this.draggingSelect = true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (this.draggingSelect && event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            this.moveCaretTo(this.indexAt((int) event.x(), (int) event.y()), true);
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        this.draggingSelect = false;
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        this.scroll = Math.max(0, this.scroll - (int) Math.signum(scrollY));
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_S) {
            this.save();
            return true;
        }
        if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_A) {
            this.selectionAnchor = 0;
            this.caret = this.text.length();
            return true;
        }
        if (event.hasControlDown() && event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (this.hasSelection()) {
                this.deleteSelection();
            } else {
                this.deleteWordBefore();
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            if (this.hasSelection()) {
                this.deleteSelection();
            } else if (this.caret > 0) {
                this.text.deleteCharAt(this.caret - 1);
                this.caret--;
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_DELETE) {
            if (this.hasSelection()) {
                this.deleteSelection();
            } else if (this.caret < this.text.length()) {
                this.text.deleteCharAt(this.caret);
            }
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            this.insert("\n");
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_LEFT) {
            this.moveCaretTo(this.caret - 1, event.hasShiftDown());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_RIGHT) {
            this.moveCaretTo(this.caret + 1, event.hasShiftDown());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_HOME) {
            this.moveCaretTo(this.lineStart(this.caret), event.hasShiftDown());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_END) {
            this.moveCaretTo(this.lineEnd(this.caret), event.hasShiftDown());
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_UP || event.key() == GLFW.GLFW_KEY_DOWN) {
            this.moveCaretVertical(event.key() == GLFW.GLFW_KEY_UP ? -1 : 1, event.hasShiftDown());
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        int code = event.codepoint();
        if (code >= 32 && code != 127) {
            this.insert(Character.toString(code));
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void save() {
        this.onSave.accept(this.text.toString());
        this.onClose();
    }

    private void insert(String value) {
        if (this.hasSelection()) {
            this.deleteSelection();
        }
        if (this.text.length() + value.length() > this.maxLength) {
            return;
        }
        this.text.insert(this.caret, value);
        this.caret += value.length();
        this.selectionAnchor = -1;
    }

    private boolean hasSelection() {
        return this.selectionAnchor >= 0 && this.selectionAnchor != this.caret;
    }

    private int selectionStart() {
        return Math.min(this.caret, this.selectionAnchor);
    }

    private int selectionEnd() {
        return Math.max(this.caret, this.selectionAnchor);
    }

    private void deleteSelection() {
        if (!this.hasSelection()) {
            return;
        }
        int start = this.selectionStart();
        int end = this.selectionEnd();
        this.text.delete(start, end);
        this.caret = start;
        this.selectionAnchor = -1;
    }

    private void deleteWordBefore() {
        if (this.caret <= 0) {
            return;
        }
        int start = TreeEditorTextInput.previousWord(this.text.toString(), this.caret);
        this.text.delete(start, this.caret);
        this.caret = start;
        this.selectionAnchor = -1;
    }

    private void moveCaretTo(int pos, boolean extend) {
        pos = Math.max(0, Math.min(this.text.length(), pos));
        if (extend) {
            if (this.selectionAnchor < 0) {
                this.selectionAnchor = this.caret;
            }
        } else {
            this.selectionAnchor = -1;
        }
        this.caret = pos;
    }

    private void moveCaretVertical(int direction, boolean extend) {
        int width = this.panelW - 30;
        List<VisualLine> lines = this.visualLines(width);
        int current = this.visualIndex(lines, this.caret);
        int next = Math.max(0, Math.min(lines.size() - 1, current + direction));
        if (next >= 0 && next < lines.size()) {
            VisualLine from = lines.get(current);
            int offset = this.caret - from.start;
            VisualLine to = lines.get(next);
            this.moveCaretTo(Math.min(to.end, to.start + offset), extend);
        }
    }

    private void drawSelection(GuiGraphicsExtractor graphics, VisualLine line, int left, int y, int selStart, int selEnd) {
        if (selStart < 0 || selEnd < 0 || selEnd <= selStart) {
            return;
        }
        int from = Math.max(selStart, line.start);
        int to = Math.min(selEnd, line.end);
        if (from < to) {
            int x1 = left + 6 + this.font.width(this.text.substring(line.start, from));
            int x2 = left + 6 + this.font.width(this.text.substring(line.start, to));
            graphics.fill(x1, y - 1, Math.max(x1 + 1, x2), y + 9, TreeEditorTheme.ACCENT_DIM);
            return;
        }
        if (line.start == line.end && selStart <= line.start && selEnd > line.start) {
            int x1 = left + 6;
            graphics.fill(x1, y - 1, x1 + 4, y + 9, TreeEditorTheme.ACCENT_DIM);
        }
    }

    private int indexAt(int mouseX, int mouseY) {
        int left = this.panelX + 10;
        int top = this.panelY + 42;
        int right = this.panelX + this.panelW - 10;
        List<VisualLine> lines = this.visualLines(right - left - 10);
        int index = this.scroll + (mouseY - top - 5) / LINE;
        if (index < 0 || lines.isEmpty()) {
            return 0;
        }
        if (index >= lines.size()) {
            return this.text.length();
        }
        return this.indexAt(lines.get(index), mouseX - left - 6);
    }

    private void ensureCaretVisible(List<VisualLine> lines, int visible) {
        int index = this.visualIndex(lines, this.caret);
        if (index < this.scroll) {
            this.scroll = index;
        } else if (index >= this.scroll + visible) {
            this.scroll = index - visible + 1;
        }
    }

    private int visualIndex(List<VisualLine> lines, int caret) {
        for (int index = 0; index < lines.size(); index++) {
            VisualLine line = lines.get(index);
            if (caret <= line.end) {
                return index;
            }
        }
        return Math.max(0, lines.size() - 1);
    }

    private int indexAt(VisualLine line, int x) {
        String raw = this.text.substring(line.start, line.end);
        int fit = raw.length();
        while (fit > 0 && this.font.width(raw.substring(0, fit)) > x) {
            fit--;
        }
        return line.start + fit;
    }

    private int lineStart(int index) {
        int start = this.text.lastIndexOf("\n", Math.max(0, index - 1));
        return start < 0 ? 0 : start + 1;
    }

    private int lineEnd(int index) {
        int end = this.text.indexOf("\n", index);
        return end < 0 ? this.text.length() : end;
    }

    private List<VisualLine> visualLines(int maxWidth) {
        List<VisualLine> lines = new ArrayList<>();
        String value = this.text.toString();
        int width = Math.max(8, maxWidth);
        int index = 0;
        while (index < value.length()) {
            if (value.charAt(index) == '\n') {
                lines.add(new VisualLine(FormattedCharSequence.EMPTY, index, index));
                index++;
                continue;
            }
            int end = index;
            while (end < value.length() && value.charAt(end) != '\n'
                    && this.font.width(value.substring(index, end + 1)) <= width) {
                end++;
            }
            if (end == index) {
                end++;
            }
            lines.add(new VisualLine(Component.literal(value.substring(index, end)).getVisualOrderText(), index, end));
            index = end;
            if (index < value.length() && value.charAt(index) == '\n') {
                index++;
            }
        }
        if (value.isEmpty() || value.endsWith("\n")) {
            lines.add(new VisualLine(FormattedCharSequence.EMPTY, value.length(), value.length()));
        }
        return lines;
    }

    private record VisualLine(FormattedCharSequence text, int start, int end) {
    }
}
