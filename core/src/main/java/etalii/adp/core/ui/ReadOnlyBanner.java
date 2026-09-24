package etalii.adp.core.ui;

import com.intellij.ui.EditorNotificationPanel;

/** Shown while the file cannot be written: it can be viewed, not edited. */
public final class ReadOnlyBanner extends EditorNotificationPanel {

    public static final String MESSAGE = "This file is read-only, so it can be viewed but not edited.";

    public ReadOnlyBanner() {
        super(Status.Warning);
        setText(MESSAGE);
    }
}
