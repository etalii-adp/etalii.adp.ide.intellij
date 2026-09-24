package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.swt.custom.CLabel;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.junit.jupiter.api.Test;

import etalii.adp.testing.DesignerDriver;

/** FR-021 and the edge cases: delete nodes with their descendants, never the root, with their arrow links. */
class DeleteTest {

    static final String DELETE = "etalii.adp.freemind.delete";

    static final String LINKED_MAP = """
            <map version="1.0.1">
            <node CREATED="1000" ID="R" MODIFIED="1000" TEXT="Root">
            <node CREATED="1000" ID="A" MODIFIED="1000" POSITION="right" TEXT="A">
            <node CREATED="1000" ID="A1" MODIFIED="1000" TEXT="A1">
            <arrowlink DESTINATION="L" ENDARROW="Default" ID="Arrow_ID_1" STARTARROW="None"/>
            </node>
            </node>
            <node CREATED="1000" ID="L" MODIFIED="1000" POSITION="left" TEXT="L">
            <arrowlink DESTINATION="A1" ENDARROW="Default" ID="Arrow_ID_2" STARTARROW="None"/>
            </node>
            </node>
            </map>
            """;

    @Test
    void deleteOneNode() {
        try (var d = DesignerDriver.openText("delete.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A2")).run(DELETE);
            assertNull(d.figureOf(key("A2")));
            assertFalse(d.text().contains("ID=\"A2\""));
            assertEquals("Delete Node", d.undoLabel());
            assertTrue(d.isDirty());
            d.undo();
            assertEquals(MAP, d.text());
            assertNotNull(d.figureOf(key("A2")));
            assertFalse(d.isDirty());
        }
    }

    @Test
    void deleteSeveralNodesWithTheirDescendants() {
        try (var d = DesignerDriver.openText("delete.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A"), key("A1"), key("L")).run(DELETE);
            for (String id : List.of("A", "A1", "A2", "A3", "L")) {
                assertNull(d.figureOf(key(id)), id);
                assertFalse(d.text().contains("ID=\"" + id + "\""), id);
            }
            assertNotNull(d.figureOf(key("B")));
            assertEquals("Delete Nodes", d.undoLabel());
            d.undo();
            assertEquals(MAP, d.text());
        }
    }

    @Test
    void theRootAloneCannotBeDeleted() {
        try (var d = DesignerDriver.openText("delete.mm", MAP, MindMapEditor.ID)) {
            d.select(key("R"));
            assertThrows(IllegalStateException.class, () -> d.run(DELETE));
            assertEquals(MAP, d.text());
            assertFalse(d.isDirty());
        }
    }

    @Test
    void withTheRootSelectedTheOthersAreDeletedAndTheUserIsTold() {
        try (var d = DesignerDriver.openText("delete.mm", MAP, MindMapEditor.ID)) {
            d.select(key("R"), key("B")).run(DELETE);
            assertNotNull(d.figureOf(key("R")));
            assertNull(d.figureOf(key("B")));
            assertEquals("Delete Node", d.undoLabel());
            assertTrue(statusTexts(d).contains("The root node cannot be deleted"), statusTexts(d).toString());
        }
    }

    @Test
    void arrowLinksIntoDeletedNodesGoAndComeBackWithOneUndo() {
        try (var d = DesignerDriver.openText("links.mm", LINKED_MAP, MindMapEditor.ID)) {
            d.select(key("A")).run(DELETE);
            assertFalse(d.text().contains("Arrow_ID_1"), "a link inside the deleted branch goes with it");
            assertFalse(d.text().contains("Arrow_ID_2"), "a link into the deleted branch is removed");
            assertTrue(d.text().contains("ID=\"L\""));
            d.undo();
            assertEquals(LINKED_MAP, d.text());
            assertNull(d.undoLabel());
            assertFalse(d.isDirty());
        }
    }

    /** The texts shown in the workbench window's labels, which include its status line. */
    static List<String> statusTexts(DesignerDriver d) {
        List<String> texts = new ArrayList<>();
        collect(d.editor().getSite().getShell(), texts);
        return texts;
    }

    private static void collect(Control control, List<String> texts) {
        if (control instanceof CLabel label && label.getText() != null) {
            texts.add(label.getText());
        } else if (control instanceof Label label && label.getText() != null) {
            texts.add(label.getText());
        }
        if (control instanceof Composite composite) {
            for (Control child : composite.getChildren()) {
                collect(child, texts);
            }
        }
    }
}
