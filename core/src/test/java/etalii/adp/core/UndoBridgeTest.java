package etalii.adp.core;

import static etalii.adp.core.TextChange.insert;
import static etalii.adp.core.TextChange.replace;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.command.CommandEvent;
import com.intellij.openapi.command.CommandListener;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.testing.ToolDriver;

/**
 * The undo bridge from a non-text editor (research R7), proved with the fake format before any
 * FreeMind editing depends on it: one {@code execute} is one named step in the IDE's Undo.
 */
@RunWith(JUnit4.class)
public class UndoBridgeTest extends FileEditorManagerTestCase {

    private static final String TEXT = FakeFormat.HEADER + "alpha\nbeta\n";

    @Override
    public void setUp() {
        super.setUp();
        FakeFormat.register(getTestRootDisposable());
    }

    private ToolDriver open() {
        return ToolDriver.openText(myFixture, "items.txt", TEXT);
    }

    private static TextChanges renameBeta() {
        return TextChanges.of(replace(FakeFormat.HEADER.length() + 6, 4, "BETA"), insert(FakeFormat.HEADER.length(), "zero\n"));
    }

    @Test
    public void oneLabelledEntryThatUndoesAndRedoesInOneStep() {
        try (var d = open()) {
            d.tool().execute("Rename Item", renameBeta());
            String edited = d.text();

            assertEquals(FakeFormat.HEADER + "zero\nalpha\nBETA\n", edited);
            assertEquals("Undo Rename Item", d.undoLabel());
            assertTrue(d.isModified());

            d.undo();
            assertEquals(TEXT, d.text());
            assertFalse(d.isModified());
            assertEquals("Redo Rename Item", d.redoLabel());

            d.redo();
            assertEquals(edited, d.text());
            assertEquals("Undo Rename Item", d.undoLabel());

            d.undo();
            assertEquals(TEXT, d.text());
            assertNull(d.undoLabel());
        }
    }

    @Test
    public void typingAfterAnEditIsItsOwnEntry() {
        try (var d = open()) {
            d.tool().execute("Rename Item", renameBeta());
            String edited = d.text();

            d.editText(t -> t + "typed\n");
            d.undo();

            assertEquals(edited, d.text());
            assertEquals("Undo Rename Item", d.undoLabel());
        }
    }

    /** The tool's change reaches the document as exactly one command, with its label. */
    @Test
    public void routesATextEditCommandToItsExecutor() {
        try (var d = open()) {
            List<String> commands = new ArrayList<>();
            getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(CommandListener.TOPIC, new CommandListener() {
                @Override
                public void commandFinished(@NotNull CommandEvent event) {
                    commands.add(event.getCommandName());
                }
            });

            d.tool().execute("Rename Item", renameBeta());

            assertEquals(List.of("Rename Item"), commands);
            assertEquals("zero", d.tool().viewOf(0).text());
        }
    }

    /** A read-only document refuses every change, and nothing lands in the history. */
    @Test
    public void refusesOtherCommands() {
        try (var d = open()) {
            d.setReadOnly(true);

            d.tool().execute("Rename Item", renameBeta());

            assertEquals(TEXT, d.text());
            assertNull(d.undoLabel());
            assertFalse(d.isModified());
        }
    }

    /** There is one history: the text side undoes the tool's change, as the tool undoes typing. */
    @Test
    public void keepsNoUndoOrRedoStackOfItsOwn() {
        try (var d = open()) {
            d.tool().execute("Rename Item", renameBeta());

            UndoManager.getInstance(getProject()).undo(d.composite().getTextEditor());
            assertEquals(TEXT, d.text());

            d.editText(t -> t + "typed\n");
            d.undo();
            assertEquals(TEXT, d.text());
        }
    }
}
