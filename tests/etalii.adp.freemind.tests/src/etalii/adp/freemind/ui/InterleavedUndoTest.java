package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.ADD_CHILD;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.cancelInPlace;
import static etalii.adp.freemind.ui.AddNodeTest.node;
import static etalii.adp.freemind.ui.DeleteTest.DELETE;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import etalii.adp.freemind.ui.figures.NodeFigure;
import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.Page;

/** FR-004, FR-006, US3-AS2: one undo history covers both pages, in the order the edits were made. */
class InterleavedUndoTest {

    @Test
    void undoRevertsVisualAndTextEditsInReverseOrderOnEitherPage() {
        try (var d = DesignerDriver.openText("interleaved.mm", MAP, MindMapEditor.ID)) {
            List<String> texts = new ArrayList<>();
            texts.add(d.text());

            // 1: visual
            d.select(key("B")).run(ADD_CHILD);
            cancelInPlace(d);
            texts.add(d.text());
            // 2: text
            d.editText(text -> text.replace("TEXT=\"A1\"", "TEXT=\"Typed A1\""));
            texts.add(d.text());
            // 3: visual
            d.showPage(Page.VISUAL);
            d.select(key("L")).run(RENAME).typeInPlace("Ell");
            texts.add(d.text());
            // 4: text
            d.editText(text -> text.replace("TEXT=\"A3\"", "TEXT=\"Typed A3\""));
            texts.add(d.text());
            // 5: visual
            d.showPage(Page.VISUAL);
            d.select(key("A2")).run(DELETE);
            texts.add(d.text());

            for (int i = 1; i < texts.size(); i++) {
                assertNotEquals(texts.get(i - 1), texts.get(i), "edit " + i + " changed the text");
            }

            // Undo 5 and 4 from the text page, 3 and 2 from the visual page, 1 from the text page.
            Page[] undoFrom = { Page.TEXT, Page.TEXT, Page.VISUAL, Page.VISUAL, Page.TEXT };
            for (int step = 0; step < undoFrom.length; step++) {
                int expected = texts.size() - 2 - step;
                d.showPage(undoFrom[step]);
                d.undo();
                assertEquals(texts.get(expected), d.text(), "after undo " + (step + 1) + " on the " + undoFrom[step] + " page");
                assertEquals(undoFrom[step] == Page.VISUAL, d.editor().isVisualPageActive(), "undo does not switch pages");
            }

            assertEquals(MAP, d.text());
            assertFalse(d.isDirty(), "undoing everything clears the dirty marker");
            assertNull(d.undoLabel());
            d.showPage(Page.VISUAL);
            assertEquals("A1", ((NodeFigure) d.figureOf(key("A1"))).text());
            assertEquals("L", ((NodeFigure) d.figureOf(key("L"))).text());
            assertNotNull(d.figureOf(key("A2")));
            assertEquals(0, node(d, key("B")).children().size());
            assertArrayEquals(MAP.getBytes(UTF_8), d.savedBytes());
        }
    }

    @Test
    void theVisualPageFollowsEachUndoOfATextEdit() {
        try (var d = DesignerDriver.openText("interleaved.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B")).run(RENAME).typeInPlace("Visual B");
            d.editText(text -> text.replace("TEXT=\"Visual B\"", "TEXT=\"Typed B\""));
            d.showPage(Page.VISUAL);
            assertEquals("Typed B", ((NodeFigure) d.figureOf(key("B"))).text());

            d.undo();
            assertTrue(d.editor().isVisualPageActive());
            assertEquals("Visual B", ((NodeFigure) d.figureOf(key("B"))).text(), "the typing is undone first");
            assertEquals("Rename Node", d.undoLabel());

            d.undo();
            assertEquals("B", ((NodeFigure) d.figureOf(key("B"))).text());
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());

            d.redo();
            assertEquals("Visual B", ((NodeFigure) d.figureOf(key("B"))).text());
            d.redo();
            assertEquals("Typed B", ((NodeFigure) d.figureOf(key("B"))).text());
            assertTrue(d.isDirty());
        }
    }
}
