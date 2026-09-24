package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.node;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IPageLayout;
import org.eclipse.ui.IViewPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.IDocumentProviderExtension3;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.ui.figures.NodeFigure;
import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.Page;

/** FR-005, US3-AS4: a change on disk follows the platform text editor's rules. */
class ExternalChangeTest {

    private static final String ON_DISK = MAP.replace("TEXT=\"B\"", "TEXT=\"Changed on disk\"");

    /** The two answers to the platform's "File Changed" question, as the first and second button. */
    private enum Answer {
        REPLACE, KEEP
    }

    @Test
    void aCleanEditorReloadsTheChangedFile() {
        try (var d = DesignerDriver.openText("external.mm", MAP, MindMapEditor.ID)) {
            d.changeOnDisk(ON_DISK);
            waitUntil(d, () -> ON_DISK.equals(d.text()));
            assertEquals(ON_DISK, d.text());
            assertFalse(d.isDirty());
            assertEquals("Changed on disk", node(d, key("B")).text());
            assertEquals("Changed on disk", ((NodeFigure) d.figureOf(key("B"))).text(), "the visual page follows");
        }
    }

    @Test
    void aCleanEditorOnTheTextPageReloadsToo() {
        try (var d = DesignerDriver.openText("external.mm", MAP, MindMapEditor.ID)) {
            d.showPage(Page.TEXT);
            d.changeOnDisk(ON_DISK);
            waitUntil(d, () -> ON_DISK.equals(d.text()));
            assertEquals(ON_DISK, d.text());
            assertFalse(d.isDirty());
            d.showPage(Page.VISUAL);
            assertEquals("Changed on disk", ((NodeFigure) d.figureOf(key("B"))).text());
        }
    }

    @Test
    void aDirtyEditorKeepsItsTextUntilAsked() {
        try (var d = DesignerDriver.openText("external.mm", MAP, MindMapEditor.ID)) {
            d.editText(text -> text.replace("TEXT=\"A1\"", "TEXT=\"Mine\""));
            String mine = d.text();
            d.changeOnDisk(ON_DISK);
            assertEquals(mine, d.text(), "a dirty editor is never replaced silently");
            assertTrue(d.isDirty());
            IDocumentProvider provider = d.editor().textEditor().getDocumentProvider();
            assertTrue(provider instanceof IDocumentProviderExtension3);
            assertFalse(((IDocumentProviderExtension3) provider).isSynchronized(d.editor().getEditorInput()),
                    "the editor knows it is out of sync with the file");
        }
    }

    @Test
    void aDirtyEditorOnTheTextPageAsksAndReplaces() throws PartInitException {
        try (var d = DesignerDriver.openText("external.mm", MAP, MindMapEditor.ID)) {
            d.editText(text -> text.replace("TEXT=\"A1\"", "TEXT=\"Mine\""));
            d.changeOnDisk(ON_DISK);

            List<String> asked = reactivateAnswering(d, Answer.REPLACE);
            assertEquals(1, asked.size(), "asked once: " + asked);
            assertEquals(ON_DISK, d.text(), "Replace loads the file");
            assertFalse(d.isDirty());
            d.showPage(Page.VISUAL);
            assertEquals("Changed on disk", ((NodeFigure) d.figureOf(key("B"))).text());
            assertEquals("A1", ((NodeFigure) d.figureOf(key("A1"))).text());
        }
    }

    @Test
    void aDirtyEditorOnTheTextPageAsksAndKeeps() throws PartInitException {
        try (var d = DesignerDriver.openText("external.mm", MAP, MindMapEditor.ID)) {
            d.editText(text -> text.replace("TEXT=\"A1\"", "TEXT=\"Mine\""));
            String mine = d.text();
            d.changeOnDisk(ON_DISK);

            List<String> asked = reactivateAnswering(d, Answer.KEEP);
            assertEquals(1, asked.size(), "asked once: " + asked);
            assertEquals(mine, d.text(), "the user's text stays");
            assertTrue(d.isDirty());
            d.showPage(Page.VISUAL);
            assertEquals("Mine", ((NodeFigure) d.figureOf(key("A1"))).text());
        }
    }

    /**
     * The question and both of its answers, once the part has been activated while the text page
     * showed: the nested text editor only asks after it has seen one activation of its own.
     */
    @Test
    void aDirtyEditorAsksOnceItsTextPageHasBeenActivated() throws PartInitException {
        for (Answer answer : Answer.values()) {
            try (var d = DesignerDriver.openText("external.mm", MAP, MindMapEditor.ID)) {
                d.showPage(Page.TEXT);
                assertEquals(List.of(), reactivateAnswering(d, answer), "nothing to ask while in sync");
                d.editText(text -> text.replace("TEXT=\"A1\"", "TEXT=\"Mine\""));
                String mine = d.text();
                d.changeOnDisk(ON_DISK);

                List<String> asked = reactivateAnswering(d, answer);
                assertEquals(1, asked.size(), answer + ": " + asked);
                assertTrue(asked.get(0).startsWith("File Changed"), asked.get(0));
                assertEquals(answer == Answer.REPLACE ? ON_DISK : mine, d.text(), answer.toString());
                assertEquals(answer == Answer.KEEP, d.isDirty(), answer.toString());
                d.showPage(Page.VISUAL);
                assertEquals(answer == Answer.REPLACE ? "Changed on disk" : "B", ((NodeFigure) d.figureOf(key("B"))).text());
            }
        }
    }

    @Test
    void aDirtyEditorOnTheVisualPageAsks() throws PartInitException {
        try (var d = DesignerDriver.openText("external.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A1")).run(RENAME).typeInPlace("Mine");
            assertTrue(d.editor().isVisualPageActive());
            assertTrue(d.isDirty());
            d.changeOnDisk(ON_DISK);

            List<String> asked = reactivateAnswering(d, Answer.REPLACE);
            assertEquals(1, asked.size(), "a dirty editor showing the visual page asks as well: " + asked);
            assertEquals(ON_DISK, d.text());
            assertFalse(d.isDirty());
            assertEquals("Changed on disk", ((NodeFigure) d.figureOf(key("B"))).text());
        }
    }

    /**
     * Activate another part and then the editor again, as a person clicking back into it does; the
     * text editor checks its file on activation. Any question dialog that opens meanwhile is
     * answered, and closed after a hard timeout so the run never blocks. Returns the title and
     * buttons of each question seen.
     */
    private static List<String> reactivateAnswering(DesignerDriver d, Answer answer) throws PartInitException {
        Display display = Display.getCurrent();
        Shell workbench = d.editor().getSite().getShell();
        List<String> asked = new ArrayList<>();
        boolean[] done = { false };
        int[] ticks = { 0 };
        Runnable watcher = new Runnable() {
            @Override
            public void run() {
                if (done[0]) {
                    return;
                }
                for (Shell shell : display.getShells()) {
                    if (shell == workbench || shell.isDisposed() || !shell.isVisible()) {
                        continue;
                    }
                    List<Button> buttons = new ArrayList<>();
                    collectPushButtons(shell, buttons);
                    if (buttons.size() < 2) {
                        continue;
                    }
                    asked.add(shell.getText() + " " + buttons.stream().map(Button::getText).toList());
                    Button pressed = buttons.get(answer == Answer.REPLACE ? 0 : 1);
                    pressed.notifyListeners(SWT.Selection, null);
                    if (!shell.isDisposed()) {
                        shell.close();
                    }
                    display.timerExec(100, this);
                    return;
                }
                if (++ticks[0] < 100) {
                    display.timerExec(100, this);
                } else {
                    for (Shell shell : display.getShells()) {
                        if (shell != workbench && !shell.isDisposed()) {
                            asked.add("timed out on " + shell.getText());
                            shell.close();
                        }
                    }
                }
            }
        };
        display.timerExec(100, watcher);

        IWorkbenchPage page = d.editor().getSite().getPage();
        IViewPart other = page.showView(IPageLayout.ID_OUTLINE);
        page.activate(other);
        d.settle();
        page.activate(d.editor());
        d.settle();
        // Give a question that opens late a moment to show and be answered.
        long until = System.currentTimeMillis() + 1500;
        while (System.currentTimeMillis() < until && asked.isEmpty()) {
            if (!display.readAndDispatch()) {
                pause();
            }
        }
        d.settle();
        done[0] = true;
        page.hideView(other);
        page.activate(d.editor());
        d.settle();
        return asked;
    }

    private static void collectPushButtons(Control control, List<Button> buttons) {
        if (control instanceof Button button && (button.getStyle() & SWT.PUSH) != 0 && button.isVisible()) {
            buttons.add(button);
        }
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                collectPushButtons(child, buttons);
            }
        }
    }

    /** Dispatch UI work until the condition holds, for at most five seconds. */
    private static void waitUntil(DesignerDriver d, BooleanSupplier condition) {
        Display display = Display.getCurrent();
        long until = System.currentTimeMillis() + 5000;
        while (!condition.getAsBoolean() && System.currentTimeMillis() < until) {
            if (!display.readAndDispatch()) {
                pause();
            }
        }
        d.settle();
        assertNotNull(display);
    }

    private static void pause() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
