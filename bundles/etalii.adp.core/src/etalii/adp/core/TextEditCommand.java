package etalii.adp.core;

import java.util.function.Supplier;

import org.eclipse.gef.commands.Command;
import org.eclipse.text.edits.TextEdit;

/**
 * A GEF command that is only a label and a text edit. {@link OperationHistoryCommandStack} hands it
 * to the editor, which runs it on the workbench history, so GEF never executes or undoes it.
 */
public class TextEditCommand extends Command {

    private final Supplier<TextEdit> edit;

    public TextEditCommand(String label, Supplier<TextEdit> edit) {
        super(label);
        this.edit = edit;
    }

    /** Build the edit against the current document text, or {@code null} for no change. */
    public TextEdit createEdit() {
        return edit.get();
    }
}
