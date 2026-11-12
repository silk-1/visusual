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
    private final String placeholderKey;
    private boolean showError;

    public TextInputWidget(Rect bounds, TextInputModel model, String placeholderKey, Consumer<String> onCommit) {
        super(bounds);
        this.model = model;
        this.onCommit = onCommit;
        this.placeholderKey = placeholderKey;
    }

    public TextInputModel model() {
        return model;
    }

    @Override
    public void render(Painter painter, ThemeTokens theme) {
        painter.fill(bounds, Color.argb(theme.surface()));
        painter.outline(bounds, Color.argb(showError ? theme.error() : focused ? theme.accent() : theme.border()), 1);
        String text = model.text();
        int textColor = Color.argb(text.isEmpty() ? theme.textSecondary() : theme.textPrimary());
        painter.text(text, bounds.x() + 4, bounds.centerY() - painter.lineHeight() / 2, textColor, false);
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
