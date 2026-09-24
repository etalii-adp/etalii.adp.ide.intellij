package etalii.adp.core;

import java.util.function.BiConsumer;

import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CommandStack;
import org.eclipse.text.edits.TextEdit;

/**
 * The edit domain's command stack. It hands every {@link TextEditCommand} to the editor, which runs
 * it on the workbench operation history, and refuses any other command. It never records anything,
 * so GEF's own undo never competes with the workbench history (research R6).
 */
public class OperationHistoryCommandStack extends CommandStack {

    private final BiConsumer<String, TextEdit> executor;

    public OperationHistoryCommandStack(BiConsumer<String, TextEdit> executor) {
        this.executor = executor;
    }

    @Override
    public void execute(Command command) {
        if (command instanceof TextEditCommand textEditCommand && command.canExecute()) {
            TextEdit edit = textEditCommand.createEdit();
            if (edit != null) {
                executor.accept(command.getLabel(), edit);
            }
        }
    }
}
