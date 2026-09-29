package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.testing.ToolDriver;
import etalii.adp.testing.Layout;

/** Spec 001 FR-006, US1: the text and the diagram show one document. */
@RunWith(JUnit4.class)
public class TextVisualSyncTest extends FileEditorManagerTestCase {

    /** A small map the viewing tests share: two branches on the right, one on the left. */
    static final String MAP = """
            <map version="1.0.1">
            <node CREATED="1000" ID="R" MODIFIED="1000" TEXT="Root">
            <node CREATED="1000" ID="A" MODIFIED="1000" POSITION="right" TEXT="A">
            <node CREATED="1000" ID="A1" MODIFIED="1000" TEXT="A1"/>
            <node CREATED="1000" ID="A2" MODIFIED="1000" TEXT="A2"/>
            <node CREATED="1000" ID="A3" MODIFIED="1000" TEXT="A3"/>
            </node>
            <node CREATED="1000" ID="B" MODIFIED="1000" POSITION="right" TEXT="B"/>
            <node CREATED="1000" ID="L" MODIFIED="1000" POSITION="left" TEXT="L"/>
            </node>
            </map>
            """;

    @Override
    public void setUp() {
        super.setUp();
    }

    private ToolDriver open() {
        return ToolDriver.openText(myFixture, "sync.mm", MAP);
    }

    @Test
    public void aTextEditShowsOnTheVisualPage() {
        try (var d = open()) {
            assertEquals(Layout.TOOL, d.layout());
            d.showLayout(Layout.TEXT);
            d.editText(text -> text.replace("TEXT=\"A2\"", "TEXT=\"Typed in the text\""));
            assertTrue(d.isModified());

            d.showLayout(Layout.TOOL);
            assertEquals("Typed in the text", node(d, key("A2")).text());
            assertEquals("Typed in the text", d.viewOf(key("A2")).text());
            assertNull(d.tool().problemMessage());
        }
    }

    @Test
    public void aTextEditShowsBesideItInTheSplitLayout() {
        try (var d = open()) {
            d.showLayout(Layout.SPLIT);
            d.editText(text -> text.replace("TEXT=\"B\"", "TEXT=\"Seen at once\""));
            assertEquals(Layout.SPLIT, d.layout());
            assertEquals("Seen at once", d.viewOf(key("B")).text());
        }
    }

    @Test
    public void nodesAddedAndRemovedInTheTextAreDrawnAndErased() {
        try (var d = open()) {
            d.editText(text -> text
                    .replace("<node CREATED=\"1000\" ID=\"B\" MODIFIED=\"1000\" POSITION=\"right\" TEXT=\"B\"/>",
                            "<node CREATED=\"1000\" ID=\"B\" MODIFIED=\"1000\" POSITION=\"right\" TEXT=\"B\">\n"
                                    + "<node CREATED=\"1000\" ID=\"B1\" MODIFIED=\"1000\" TEXT=\"Typed child\"/>\n</node>")
                    .replace("<node CREATED=\"1000\" ID=\"A3\" MODIFIED=\"1000\" TEXT=\"A3\"/>\n", ""));

            assertNotNull("the node typed in the text is drawn", d.viewOf(key("B1")));
            assertEquals("Typed child", d.viewOf(key("B1")).text());
            assertEquals(key("B"), node(d, key("B1")).parent().key());
            assertNull("the node removed from the text is gone", d.viewOf(key("A3")));
            assertEquals(List.of(key("A1"), key("A2")), node(d, key("A")).children().stream().map(MapNode::key).toList());
        }
    }

    @Test
    public void switchingPagesKeepsTheVisualSelection() {
        try (var d = open()) {
            d.select(key("A1"), key("B"));
            assertEquals(List.of(key("A1"), key("B")), d.selectedKeys());

            for (int round = 0; round < 3; round++) {
                d.showLayout(Layout.TEXT);
                assertEquals(Layout.TEXT, d.layout());
                d.showLayout(Layout.TOOL);
                assertEquals(Layout.TOOL, d.layout());
                assertEquals("round " + round, List.of(key("A1"), key("B")), d.selectedKeys());
            }
        }
    }

    @Test
    public void aTextEditKeepsTheSelectionByKey() {
        try (var d = open()) {
            d.select(key("A2"));
            // A node typed before A2 moves A2's index, but not its key.
            d.editText(text -> text.replace("<node CREATED=\"1000\" ID=\"A1\"",
                    "<node CREATED=\"1000\" ID=\"A0\" MODIFIED=\"1000\" TEXT=\"A0\"/>\n<node CREATED=\"1000\" ID=\"A1\""));
            d.editText(text -> text.replace("TEXT=\"A2\"", "TEXT=\"A2 renamed\""));

            assertEquals(List.of(key("A2")), d.selectedKeys());
            assertEquals("A2 renamed", d.viewOf(key("A2")).text());
            assertEquals(List.of(key("A0"), key("A1"), key("A2"), key("A3")),
                    node(d, key("A")).children().stream().map(MapNode::key).toList());
        }
    }

    @Test
    public void aRemovedNodeLeavesTheSelection() {
        try (var d = open()) {
            d.select(key("A3"), key("B"));
            d.editText(text -> text.replace("<node CREATED=\"1000\" ID=\"A3\" MODIFIED=\"1000\" TEXT=\"A3\"/>\n", ""));
            assertEquals(List.of(key("B")), d.selectedKeys());
        }
    }

    @Test
    public void breakingAndFixingTheTextKeepsTheSelectionAndFolding() {
        try (var d = open()) {
            MindMapFileEditor tool = LayoutTest.tool(d);
            d.select(key("A2"));
            tool.setShownFolded(tool.model().node(key("A")), true);
            assertNull(d.viewOf(key("A2")));

            d.editText(text -> text.replace("<node CREATED=\"1000\" ID=\"B\"", "<node CREATED=\"1000\" ID=\"B\" ID=\"B\""));
            assertTrue(d.problemShown());
            assertNull(d.tool().model());

            d.editText(text -> MAP);
            assertFalse(d.problemShown());
            assertEquals("the selection survives the broken text", List.of(key("A2")), d.selectedKeys());
            assertTrue("display folding survives too", tool.isShownFolded(tool.model().node(key("A"))));
        }
    }

    static MapNode node(ToolDriver d, NodeKey key) {
        MapNode node = LayoutTest.tool(d).model().node(key);
        assertNotNull(key + " is in the model", node);
        return node;
    }
}
