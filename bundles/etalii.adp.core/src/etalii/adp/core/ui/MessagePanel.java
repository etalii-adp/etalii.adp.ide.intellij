package etalii.adp.core.ui;

import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;

/**
 * A strip above the visual page: the read-only banner, or the panel explaining why the document
 * cannot be shown, with a "Show text" button. Hidden panels take no space.
 */
public final class MessagePanel extends Composite {

    private final Label icon;
    private final Label text;
    private final Button button;
    private Runnable action;
    private boolean shown;

    public MessagePanel(Composite parent) {
        super(parent, SWT.NONE);
        setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
        GridLayout layout = new GridLayout(3, false);
        layout.marginWidth = 8;
        layout.marginHeight = 6;
        setLayout(layout);

        icon = new Label(this, SWT.NONE);
        icon.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        text = new Label(this, SWT.WRAP);
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false));
        button = new Button(this, SWT.PUSH);
        button.setText("Show text");
        button.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false));
        button.addListener(SWT.Selection, e -> {
            if (action != null) {
                action.run();
            }
        });
        hidePanel();
    }

    /** An explanation without an action, such as why the file is read-only. */
    public void showBanner(String message) {
        show(SWT.ICON_INFORMATION, message, null);
    }

    /** The document cannot be shown; {@code line} and {@code column} are one-based. */
    public void showProblem(String message, int line, int column, Runnable showText) {
        show(SWT.ICON_ERROR, "This file cannot be shown in the designer. Line " + line + ", column " + column + ": " + message, showText);
    }

    public void hidePanel() {
        action = null;
        text.setText("");
        setShown(false);
    }

    /** The text shown, or {@code null} when the panel is hidden. */
    public String message() {
        return shown ? text.getText() : null;
    }

    private void show(int iconId, String message, Runnable showText) {
        icon.setImage(getDisplay().getSystemImage(iconId));
        text.setText(message);
        action = showText;
        button.setVisible(showText != null);
        ((GridData) button.getLayoutData()).exclude = showText == null;
        setShown(true);
    }

    private void setShown(boolean shown) {
        this.shown = shown;
        ((GridData) getLayoutData()).exclude = !shown;
        setVisible(shown);
        getParent().layout(true, true);
    }
}
