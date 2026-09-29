package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.wm.StatusBar;
import com.intellij.openapi.wm.StatusBarInfo;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.ui.actions.MindMapAction;
import etalii.adp.testing.ToolDriver;

/** Spec 001 FR-021 and its edge cases: nodes go with their descendants and the arrow links into them, never the root. */
@RunWith(JUnit4.class)
public class DeleteTest extends FileEditorManagerTestCase {

    static final String DELETE = "etalii.adp.freemind.Delete";

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

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void deleteOneNode() {
        try (var d = ToolDriver.openText(myFixture, "delete.mm", MAP)) {
            d.select(key("A2")).press("DELETE");
            assertNull(d.viewOf(key("A2")));
            assertFalse(d.text().contains("ID=\"A2\""));
            assertEquals("Undo Delete Node", d.undoLabel());
            assertTrue(d.isModified());
            assertEquals("what remains nearest is selected", List.of(key("A")), d.selectedKeys());
            d.undo();
            assertEquals(MAP, d.text());
            assertNotNull(d.viewOf(key("A2")));
            assertFalse(d.isModified());
        }
    }

    @Test
    public void deleteSeveralNodesWithTheirDescendants() {
        try (var d = ToolDriver.openText(myFixture, "delete.mm", MAP)) {
            d.select(key("A"), key("A1"), key("L")).run(DELETE);
            for (String id : List.of("A", "A1", "A2", "A3", "L")) {
                assertNull(id, d.viewOf(key(id)));
                assertFalse(id, d.text().contains("ID=\"" + id + "\""));
            }
            assertNotNull(d.viewOf(key("B")));
            assertEquals("Undo Delete Nodes", d.undoLabel());
            d.undo();
            assertEquals(MAP, d.text());
        }
    }

    @Test
    public void theRootAloneCannotBeDeleted() {
        try (var d = ToolDriver.openText(myFixture, "delete.mm", MAP)) {
            d.select(key("R"));
            assertFalse(d.presentation(DELETE).isEnabled());
            assertEquals("the reason is stated", MindMapAction.ROOT_CANNOT_BE_DELETED, d.presentation(DELETE).getDescription());
            d.run(DELETE).press("DELETE");
            assertEquals(MAP, d.text());
            assertFalse(d.isModified());
        }
    }

    @Test
    public void withTheRootSelectedTheOthersAreDeletedAndTheUserIsTold() {
        List<String> told = new ArrayList<>();
        getProject().getMessageBus().connect(getTestRootDisposable()).subscribe(StatusBar.Info.INSTANCE.getTOPIC(), new StatusBarInfo() {
            @Override
            public void setInfo(@Nullable String text) {
                told.add(text);
            }

            @Override
            public void setInfo(@Nullable String text, @Nullable String requestor) {
                told.add(text);
            }

            @Override
            public @NotNull String getInfo() {
                return "";
            }
        });
        try (var d = ToolDriver.openText(myFixture, "delete.mm", MAP)) {
            d.select(key("R"), key("B"));
            assertTrue(d.presentation(DELETE).isEnabled());
            d.run(DELETE);
            assertNotNull(d.viewOf(key("R")));
            assertNull(d.viewOf(key("B")));
            assertEquals("Undo Delete Node", d.undoLabel());
            assertTrue(told.toString(), told.contains(MindMapAction.ROOT_CANNOT_BE_DELETED));
        }
    }

    @Test
    public void arrowLinksIntoDeletedNodesGoAndComeBackWithOneUndo() {
        try (var d = ToolDriver.openText(myFixture, "links.mm", LINKED_MAP)) {
            d.select(key("A")).run(DELETE);
            assertFalse("a link inside the deleted branch goes with it", d.text().contains("Arrow_ID_1"));
            assertFalse("a link into the deleted branch is removed", d.text().contains("Arrow_ID_2"));
            assertTrue(d.text().contains("ID=\"L\""));
            d.undo();
            assertEquals(LINKED_MAP, d.text());
            assertNull(d.undoLabel());
            assertFalse(d.isModified());
        }
    }
}
