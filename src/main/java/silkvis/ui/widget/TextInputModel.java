package silkvis.ui.widget;

import java.util.function.Predicate;

/**
 * Editable single-line text model (UI-05): cursor, selection, validation and
 * clipboard-free editing primitives. Invalid input never becomes a committed value.
 */
public final class TextInputModel {
    private String text;
    private int cursor;
    private int selectionAnchor;
    private final int maxLength;
    private final Predicate<String> validator;

    public TextInputModel(String initial, int maxLength, Predicate<String> validator) {
        this.maxLength = Math.max(1, maxLength);
        this.validator = validator;
        setText(initial == null ? "" : initial);
    }

    public String text() {
        return text;
    }

    public void setText(String value) {
        String next = value == null ? "" : value;
        if (next.length() > maxLength) {
            next = next.substring(0, maxLength);
        }
        this.text = next;
        this.cursor = next.length();
        this.selectionAnchor = cursor;
    }

    public int cursor() {
        return cursor;
    }

    public boolean isValid() {
        return validator == null || validator.test(text);
    }

    public boolean hasSelection() {
        return cursor != selectionAnchor;
    }

    public int selectionStart() {
        return Math.min(cursor, selectionAnchor);
    }

    public int selectionEnd() {
        return Math.max(cursor, selectionAnchor);
    }

    public String selectedText() {
        return text.substring(selectionStart(), selectionEnd());
    }

    public void selectAll() {
        selectionAnchor = 0;
        cursor = text.length();
    }

    /** @return true when the text changed */
    public boolean insert(String insert) {
        if (insert == null || insert.isEmpty()) {
            return false;
        }
        if (hasSelection()) {
            text = text.substring(0, selectionStart()) + text.substring(selectionEnd());
            cursor = selectionStart();
            selectionAnchor = cursor;
        }
        StringBuilder sb = new StringBuilder(text);
        String clean = insert.replaceAll("[\\r\\n\\t]", " ");
        int room = maxLength - text.length();
        if (room <= 0) {
            return false;
        }
        if (clean.length() > room) {
            clean = clean.substring(0, room);
        }
        sb.insert(cursor, clean);
        text = sb.toString();
        cursor += clean.length();
        selectionAnchor = cursor;
        return true;
    }

    public boolean backspace() {
        if (hasSelection()) {
            return deleteSelection();
        }
        if (cursor == 0) {
            return false;
        }
        text = text.substring(0, cursor - 1) + text.substring(cursor);
        cursor--;
        selectionAnchor = cursor;
        return true;
    }

    public boolean deleteForward() {
        if (hasSelection()) {
            return deleteSelection();
        }
        if (cursor >= text.length()) {
            return false;
        }
        text = text.substring(0, cursor) + text.substring(cursor + 1);
        return true;
    }

    private boolean deleteSelection() {
        text = text.substring(0, selectionStart()) + text.substring(selectionEnd());
        cursor = selectionStart();
        selectionAnchor = cursor;
        return true;
    }

    public void moveCursor(int delta, boolean select) {
        cursor = Math.max(0, Math.min(text.length(), cursor + delta));
        if (!select) {
            selectionAnchor = cursor;
        }
    }

    public void moveTo(int index, boolean select) {
        cursor = Math.max(0, Math.min(text.length(), index));
        if (!select) {
            selectionAnchor = cursor;
        }
    }

    public boolean keyPressed(int keyCode, int modifiers) {
        boolean shift = (modifiers & Keys.MOD_SHIFT) != 0;
        boolean ctrl = (modifiers & Keys.MOD_CONTROL) != 0;
        switch (keyCode) {
            case Keys.BACKSPACE -> {
                return backspace();
            }
            case Keys.DELETE -> {
                return deleteForward();
            }
            case Keys.LEFT -> {
                moveCursor(-1, shift);
                return true;
            }
            case Keys.RIGHT -> {
                moveCursor(1, shift);
                return true;
            }
            case Keys.HOME -> {
                moveTo(0, shift);
                return true;
            }
            case Keys.END -> {
                moveTo(text.length(), shift);
                return true;
            }
            case Keys.A -> {
                if (ctrl) {
                    selectAll();
                    return true;
                }
                return false;
            }
            default -> {
                return false;
            }
        }
    }
}
