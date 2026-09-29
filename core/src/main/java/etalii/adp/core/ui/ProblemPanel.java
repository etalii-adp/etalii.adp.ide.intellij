package etalii.adp.core.ui;

import com.intellij.ui.EditorNotificationPanel;

/**
 * Why the tool cannot show the file: the message, line and column, and a "Show Text" link to
 * the text view. The file is never changed because of a problem.
 */
public final class ProblemPanel extends EditorNotificationPanel {

    private final String message;

    public ProblemPanel(String problem, int line, int column, Runnable showText) {
        super(Status.Error);
        this.message = problem + " (Line " + line + ", column " + column + ")";
        setText(message);
        createActionLabel("Show Text", showText::run);
    }

    /** The text shown, including the line and column. */
    public String message() {
        return message;
    }
}
