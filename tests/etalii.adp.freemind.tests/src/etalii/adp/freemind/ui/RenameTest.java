package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.inPlaceEditor;
import static etalii.adp.freemind.ui.AddNodeTest.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.gef.EditPart;
import org.eclipse.gef.Request;
import org.eclipse.gef.RequestConstants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.ui.figures.NodeFigure;
import etalii.adp.testing.DesignerDriver;

/** FR-020, US2-AS2: rename in place by F2 and by double-click; a rich node warns first. */
class RenameTest {

    static final String RENAME = "etalii.adp.freemind.rename";

    static final String RICH_MAP = """
            <map version="1.0.1">
            <node CREATED="1000" ID="R" MODIFIED="1000" TEXT="Root">
            <node CREATED="1000" ID="H" MODIFIED="1000" POSITION="right"><richcontent TYPE="NODE"><html>
              <head>
              </head>
              <body>
                <p>
                  Hello <b>rich</b> world
                </p>
              </body>
            </html>
            </richcontent>
            <richcontent TYPE="NOTE"><html><head></head><body><p>Keep this note</p></body></html></richcontent>
            </node>
            </node>
            </map>
            """;

    private final NodeRenameManager.Confirmer original = NodeRenameManager.confirmer;
    private final List<String> asked = new ArrayList<>();

    @AfterEach
    void restoreConfirmer() {
        NodeRenameManager.confirmer = original;
    }

    @Test
    void renameByF2() {
        try (var d = DesignerDriver.openText("rename.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B")).run(RENAME);
            assertEquals("B", inPlaceEditor(d).getText(), "the editor starts from the node's text");
            d.typeInPlace("Bee");
            assertEquals("Bee", node(d, key("B")).text());
            assertEquals("Bee", ((NodeFigure) d.figureOf(key("B"))).text());
            assertNotEquals(Long.valueOf(1000), node(d, key("B")).modified(), "MODIFIED is updated (FR-011)");
            assertEquals("Rename Node", d.undoLabel());
            assertEquals(List.of(key("B")), d.selectedModels());
            assertTrue(d.isDirty());
            d.undo();
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
        }
    }

    @Test
    void renameByDoubleClick() {
        try (var d = DesignerDriver.openText("rename.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A2"));
            EditPart part = d.editor().viewer().getEditPartRegistry().get(key("A2"));
            part.performRequest(new Request(RequestConstants.REQ_OPEN));
            d.settle();
            assertNotNull(inPlaceEditor(d));
            d.typeInPlace("Second");
            assertEquals("Second", node(d, key("A2")).text());
            assertEquals("Rename Node", d.undoLabel());
        }
    }

    @Test
    void unchangedTextIsNoEdit() {
        try (var d = DesignerDriver.openText("rename.mm", MAP, MindMapEditor.ID)) {
            d.select(key("B")).run(RENAME).typeInPlace("B");
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
            assertNull(d.undoLabel());
        }
    }

    @Test
    void renameNeedsExactlyOneNode() {
        try (var d = DesignerDriver.openText("rename.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A"), key("B"));
            assertThrows(IllegalStateException.class, () -> d.run(RENAME));
            assertNull(inPlaceEditor(d));
        }
    }

    @Test
    void aRichNodeWarnsAndCancelChangesNothing() {
        NodeRenameManager.confirmer = (shell, title, message) -> {
            asked.add(message);
            return false;
        };
        try (var d = DesignerDriver.openText("rich.mm", RICH_MAP, MindMapEditor.ID)) {
            d.select(key("H")).run(RENAME);
            assertEquals("Hello rich world", inPlaceEditor(d).getText());
            d.typeInPlace("Plain");
            assertEquals(1, asked.size(), "the user is warned before the edit");
            assertTrue(asked.get(0).contains("plain text"), asked.get(0));
            assertEquals(RICH_MAP, d.text());
            assertFalse(d.isDirty());
            assertNull(d.undoLabel());
        }
    }

    @Test
    void aRichNodeConfirmedBecomesPlainText() {
        NodeRenameManager.confirmer = (shell, title, message) -> {
            asked.add(message);
            return true;
        };
        try (var d = DesignerDriver.openText("rich.mm", RICH_MAP, MindMapEditor.ID)) {
            d.select(key("H")).run(RENAME).typeInPlace("Plain");
            assertEquals(1, asked.size());
            assertFalse(d.text().contains("richcontent TYPE=\"NODE\""), d.text());
            assertTrue(d.text().contains("TEXT=\"Plain\""), d.text());
            assertTrue(d.text().contains("<p>Keep this note</p>"), "the note stays");
            assertFalse(node(d, key("H")).rich());
            assertEquals("Plain", node(d, key("H")).text());
            assertEquals("Rename Node", d.undoLabel());
            d.undo();
            assertEquals(RICH_MAP, d.text());
        }
    }

    @Test
    void aPlainNodeIsNotWarned() {
        NodeRenameManager.confirmer = (shell, title, message) -> {
            asked.add(message);
            return false;
        };
        try (var d = DesignerDriver.openText("rename.mm", MAP, MindMapEditor.ID)) {
            d.select(key("L")).run(RENAME).typeInPlace("Left");
            assertTrue(asked.isEmpty());
            assertEquals("Left", node(d, key("L")).text());
        }
    }
}
