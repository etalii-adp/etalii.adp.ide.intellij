package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static etalii.adp.freemind.ui.AddNodeTest.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.ui.figures.NodeFigure;
import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.Page;

/** FR-006, US3-AS1: the text page and the visual page show one document. */
class TextVisualSyncTest {

    @Test
    void aTextEditShowsOnTheVisualPage() {
        try (var d = DesignerDriver.openText("sync.mm", MAP, MindMapEditor.ID)) {
            assertTrue(d.editor().isVisualPageActive());
            d.editText(text -> text.replace("TEXT=\"A2\"", "TEXT=\"Typed in the text\""));
            assertFalse(d.editor().isVisualPageActive(), "editing the text happens on the text page");
            assertTrue(d.isDirty());

            d.showPage(Page.VISUAL);
            assertTrue(d.editor().isVisualPageActive());
            assertEquals("Typed in the text", node(d, key("A2")).text());
            assertEquals("Typed in the text", ((NodeFigure) d.figureOf(key("A2"))).text());
            assertNull(d.editor().problemMessage());
        }
    }

    @Test
    void nodesAddedAndRemovedInTheTextAreDrawnAndErased() {
        try (var d = DesignerDriver.openText("sync.mm", MAP, MindMapEditor.ID)) {
            d.editText(text -> text
                    .replace("<node CREATED=\"1000\" ID=\"B\" MODIFIED=\"1000\" POSITION=\"right\" TEXT=\"B\"/>",
                            "<node CREATED=\"1000\" ID=\"B\" MODIFIED=\"1000\" POSITION=\"right\" TEXT=\"B\">\n"
                                    + "<node CREATED=\"1000\" ID=\"B1\" MODIFIED=\"1000\" TEXT=\"Typed child\"/>\n</node>")
                    .replace("<node CREATED=\"1000\" ID=\"A3\" MODIFIED=\"1000\" TEXT=\"A3\"/>\n", ""));
            d.showPage(Page.VISUAL);

            assertNotNull(d.figureOf(key("B1")), "the node typed in the text is drawn");
            assertEquals("Typed child", ((NodeFigure) d.figureOf(key("B1"))).text());
            assertEquals(key("B"), node(d, key("B1")).parent().key());
            assertNull(d.figureOf(key("A3")), "the node removed from the text is gone");
            assertEquals(List.of(key("A1"), key("A2")), node(d, key("A")).children().stream().map(MapNode::key).toList());
        }
    }

    @Test
    void switchingPagesKeepsTheVisualSelection() {
        try (var d = DesignerDriver.openText("sync.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A1"), key("B"));
            assertEquals(List.of(key("A1"), key("B")), d.selectedModels());

            for (int round = 0; round < 3; round++) {
                d.showPage(Page.TEXT);
                assertFalse(d.editor().isVisualPageActive());
                d.showPage(Page.VISUAL);
                assertTrue(d.editor().isVisualPageActive());
                assertEquals(List.of(key("A1"), key("B")), d.selectedModels(), "round " + round);
            }
        }
    }

    @Test
    void aTextEditKeepsTheSelectionByKey() {
        try (var d = DesignerDriver.openText("sync.mm", MAP, MindMapEditor.ID)) {
            d.select(key("A2"));
            // A node typed before A2 moves A2's index, but not its key.
            d.editText(text -> text.replace("<node CREATED=\"1000\" ID=\"A1\"",
                    "<node CREATED=\"1000\" ID=\"A0\" MODIFIED=\"1000\" TEXT=\"A0\"/>\n<node CREATED=\"1000\" ID=\"A1\""));
            d.editText(text -> text.replace("TEXT=\"A2\"", "TEXT=\"A2 renamed\""));
            d.showPage(Page.VISUAL);

            assertEquals(List.of(key("A2")), d.selectedModels());
            assertEquals("A2 renamed", ((NodeFigure) d.figureOf(key("A2"))).text());
            assertEquals(List.of(key("A0"), key("A1"), key("A2"), key("A3")),
                    node(d, key("A")).children().stream().map(MapNode::key).toList());
        }
    }
}
