package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;

import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.properties.PropertiesToolWindowFactory;
import etalii.adp.testing.DiagramDriver;
import etalii.adp.testing.DiagramDriver.PropertyRows;

/** T089: the ADP Properties panel on a FreeMind map; each edit is one undo step, and what the panel cannot keep is read-only. */
@RunWith(JUnit4.class)
public class FreeMindPropertiesTest extends FileEditorManagerTestCase {

    static final String MAP = """
            <map version="1.0.1">
            <node CREATED="1000" ID="R" MODIFIED="1000" TEXT="Root">
            <node CREATED="1000" ID="A" MODIFIED="1000" POSITION="right" TEXT="A">
            <node CREATED="1000" ID="A1" MODIFIED="1000" TEXT="A1"/>
            </node>
            <node CREATED="1000" ID="B" MODIFIED="1000" POSITION="right" TEXT="B"/>
            <node CREATED="1000" ID="H" MODIFIED="1000" POSITION="left"><richcontent TYPE="NODE"><html><head></head><body><p>Hello <b>rich</b> world</p></body></html></richcontent>
            </node>
            </node>
            </map>
            """;

    private static final String B = "<node CREATED=\"1000\" ID=\"B\" MODIFIED=\"1000\" POSITION=\"right\" TEXT=\"B\"/>";

    @Override
    public void setUp() {
        super.setUp();
        install(getProject(), getTestRootDisposable());
    }

    /** Register ADP Properties and fill it with its factory, as the IDE does from the descriptor; the headless tool window manager does not. */
    static ToolWindow install(Project project, Disposable disposable) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow window = manager.registerToolWindow(RegisterToolWindowTask.notClosable(PropertiesToolWindowFactory.ID, ToolWindowAnchor.RIGHT));
        new PropertiesToolWindowFactory().createToolWindowContent(project, window);
        Disposer.register(disposable, () -> {
            window.getContentManager().removeAllContents(true);
            manager.unregisterToolWindow(PropertiesToolWindowFactory.ID);
        });
        return window;
    }

    private DiagramDriver open() {
        return DiagramDriver.openText(myFixture, "properties.mm", MAP);
    }

    @Test
    public void theRowsAreTheNodesValues() {
        try (var d = open()) {
            d.driver().select(key("B"));
            PropertyRows rows = d.properties();
            assertEquals("B", rows.row(FreeMindMapping.TEXT).value());
            assertEquals("B", rows.row(FreeMindMapping.ID).value());
            assertTrue("the ID is read-only", rows.row(FreeMindMapping.ID).readOnly());
            for (String editable : List.of(FreeMindMapping.TEXT, FreeMindMapping.FOLDED, FreeMindMapping.LINK, FreeMindMapping.COLOR,
                    FreeMindMapping.BACKGROUND_COLOR)) {
                assertFalse(editable, rows.row(editable).readOnly());
            }
        }
    }

    @Test
    public void aRichNodesTextIsReadOnly() {
        try (var d = open()) {
            d.driver().select(key("H"));
            assertEquals("Hello rich world", d.properties().row(FreeMindMapping.TEXT).value());
            assertTrue(d.properties().row(FreeMindMapping.TEXT).readOnly());
            assertFalse("its colour can still be set", d.properties().row(FreeMindMapping.COLOR).readOnly());
        }
    }

    @Test
    public void theTextIsARename() {
        try (var d = open()) {
            d.driver().select(key("B"));
            d.setProperty(FreeMindMapping.TEXT, "Bee");
            assertTrue(d.driver().text(), d.driver().text().contains("TEXT=\"Bee\""));
            assertFalse("MODIFIED is updated as by a rename", d.driver().text().contains("ID=\"B\" MODIFIED=\"1000\""));
            assertEquals("Undo Rename Node", d.driver().undoLabel());
            d.driver().undo();
            assertEquals(MAP, d.driver().text());
        }
    }

    @Test
    public void colourAndBackgroundColourAreOneStepEach() {
        try (var d = open()) {
            d.driver().select(key("B"));
            d.setProperty(FreeMindMapping.COLOR, "#ff0000");
            assertTrue(d.driver().text(), d.driver().text().contains(B.replace("<node CREATED", "<node COLOR=\"#ff0000\" CREATED")));
            assertEquals("Undo Change Colour", d.driver().undoLabel());

            d.setProperty(FreeMindMapping.BACKGROUND_COLOR, "#ccffcc");
            assertTrue(d.driver().text(), d.driver().text().contains("<node BACKGROUND_COLOR=\"#ccffcc\" COLOR=\"#ff0000\" CREATED"));
            assertEquals("Undo Change Background Colour", d.driver().undoLabel());

            d.driver().undo();
            assertTrue(d.driver().text().contains(B.replace("<node CREATED", "<node COLOR=\"#ff0000\" CREATED")));
            d.driver().undo();
            assertEquals(MAP, d.driver().text());
        }
    }

    @Test
    public void foldedIsAFold() {
        try (var d = open()) {
            d.driver().select(key("A"));
            assertEquals("false", d.properties().row(FreeMindMapping.FOLDED).value());
            d.setProperty(FreeMindMapping.FOLDED, "true");
            assertTrue(d.driver().text(), d.driver().text().contains("<node CREATED=\"1000\" FOLDED=\"true\" ID=\"A\""));
            assertEquals("Undo Fold Branch", d.driver().undoLabel());
            assertNull("the branch is folded", d.driver().viewOf(key("A1")));
            d.driver().undo();
            assertEquals(MAP, d.driver().text());
        }
    }

    @Test
    public void linkIsOneStep() {
        try (var d = open()) {
            d.driver().select(key("B"));
            d.setProperty(FreeMindMapping.LINK, "https://example.com");
            assertTrue(d.driver().text(), d.driver().text().contains("ID=\"B\" LINK=\"https://example.com\" MODIFIED"));
            assertEquals("Undo Change Link", d.driver().undoLabel());
            assertTrue("the link indicator shows", d.driver().viewOf(key("B")).hasLink());
            d.driver().undo();
            assertEquals(MAP, d.driver().text());
        }
    }
}
