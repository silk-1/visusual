package silkvis.ui.widget;

import silkvis.ui.Color;
import silkvis.ui.Painter;
import silkvis.ui.Rect;
import silkvis.ui.ThemeTokens;

import java.util.function.Consumer;

/**
 * Single-line text field (UI-05). Validation happens before commit: while the text is
 * invalid the field shows an error hint and {@code onCommit} is not called.
 */
public final class TextInputWidget extends Widget {
    private final TextInputModel model;
    private final Consumer<String> onCommit;
    private final String placeholder;
    private boolean showError;

    /**
     * @param placeholder already-localized hint text, drawn while the field is empty; the
     *                    {@code ui} package must not depend on Minecraft translation classes
     */
    public TextInputWidget(Rect bounds, TextInputModel model, String placeholder, Consumer<String> onCommit) {
        super(bounds);
        this.model = model;
        this.onCommit = onCommit;
        this.placeholder = placeholder;
    }

    public TextInputModel model() {
        return model;
    }

    @Override
    public void render(Painter painter, ThemeTokens theme) {
        painter.fill(bounds, Color.argb(theme.surface()));
        painter.outline(bounds, Color.argb(showError ? theme.error() : focused ? theme.accent() : theme.border()), 1);
        String text = model.text();
        int textY = bounds.centerY() - painter.lineHeight() / 2;
        // The hint used to be received and never drawn, so the search field rendered as a
        // blank box with no way to tell what it was (first in-game run, 2026-11-12).
        if (text.isEmpty() && placeholder != null && !placeholder.isEmpty()) {
            painter.text(placeholder, bounds.x() + 4, textY, Color.argb(theme.textSecondary()), false);
        }
        if (!text.isEmpty()) {
            painter.text(text, bounds.x() + 4, textY, Color.argb(theme.textPrimary()), false);
        }
        if (focused) {
            int caretX = bounds.x() + 4 + painter.textWidth(text.substring(0, model.cursor()));
            painter.fill(new Rect(caretX, bounds.y() + 3, 1, bounds.height() - 6), Color.argb(theme.accent()));
        }
    }

    public void commit() {
        if (!model.isValid()) {
            showError = true;
            return;
        }
        showError = false;
        if (onCommit != null) {
            onCommit.accept(model.text());
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return enabled && button == 0 && hit(mouseX, mouseY);
    }

    @Override
    public boolean keyPressed(int keyCode, int modifiers) {
        if (!enabled) {
            return false;
        }
        if (keyCode == Keys.ENTER) {
            commit();
            return true;
        }
        return model.keyPressed(keyCode, modifiers);
    }

    @Override
    public boolean charTyped(char chr) {
        return enabled && model.insert(String.valueOf(chr));
    }
}
