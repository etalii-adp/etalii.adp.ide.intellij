package etalii.adp.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.gef.commands.Command;
import org.eclipse.text.edits.InsertEdit;
import org.eclipse.text.edits.TextEdit;
import org.junit.jupiter.api.Test;

class OperationHistoryCommandStackTest {

    private final List<String> labels = new ArrayList<>();
    private final List<TextEdit> edits = new ArrayList<>();
    private final OperationHistoryCommandStack stack = new OperationHistoryCommandStack((label, edit) -> {
        labels.add(label);
        edits.add(edit);
    });

    @Test
    void routesATextEditCommandToItsExecutor() {
        TextEdit edit = new InsertEdit(0, "x");

        stack.execute(new TextEditCommand("Move Node", () -> edit));

        assertEquals(List.of("Move Node"), labels);
        assertSame(edit, edits.get(0));
    }

    @Test
    void refusesOtherCommands() {
        boolean[] ran = { false };

        stack.execute(new Command("Other") {
            @Override
            public void execute() {
                ran[0] = true;
            }
        });

        assertFalse(ran[0]);
        assertEquals(List.of(), labels);
    }

    @Test
    void keepsNoUndoOrRedoStackOfItsOwn() {
        stack.execute(new TextEditCommand("Move Node", () -> new InsertEdit(0, "x")));

        assertFalse(stack.canUndo());
        assertFalse(stack.canRedo());
        assertEquals(0, stack.getCommands().length);
        assertFalse(stack.isDirty());
    }
}
