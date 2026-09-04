package com.github.bandithelps.gui.screens;

import net.minecraft.client.gui.components.EditBox;

public final class TreeEditorTextInput {
    private TreeEditorTextInput() {
    }

    public static int previousWord(String text, int caret) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int start = Math.max(0, Math.min(caret, text.length()));
        while (start > 0 && Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        return start;
    }

    public static boolean deleteWordBefore(EditBox box) {
        if (box == null || !box.isFocused() || !box.active) {
            return false;
        }
        String highlighted = box.getHighlighted();
        if (highlighted != null && !highlighted.isEmpty()) {
            box.insertText("");
            return true;
        }
        String value = box.getValue();
        int cursor = box.getCursorPosition();
        int start = previousWord(value, cursor);
        if (start >= cursor) {
            return true;
        }
        box.setValue(value.substring(0, start) + value.substring(cursor));
        box.moveCursorTo(start, false);
        return true;
    }
}
