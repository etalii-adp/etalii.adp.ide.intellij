package etalii.adp.drawio;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;

import java.awt.geom.Point2D;
import java.nio.file.Files;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.properties.PropertiesToolWindowFactory;
import etalii.adp.core.diagram.toolbox.ToolboxToolWindowFactory;
import etalii.adp.testing.DiagramDriver;
import etalii.adp.testing.Layout;

/** T103: the draw.io designer in a headless IDE, registered from {@code adp-drawio.xml} as the plug-in ships it. */
@RunWith(JUnit4.class)
public class DrawioDesignerTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
        install(getProject(), getTestRootDisposable(), ToolboxToolWindowFactory.ID, new ToolboxToolWindowFactory());
        install(getProject(), getTestRootDisposable(), PropertiesToolWindowFactory.ID, new PropertiesToolWindowFactory());
    }

    /** Register a tool window and fill it with its factory, as the IDE does from the descriptor; the headless tool window manager does not. */
    private static void install(Project project, Disposable disposable, String id, com.intellij.openapi.wm.ToolWindowFactory factory) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow window = manager.registerToolWindow(RegisterToolWindowTask.notClosable(id, ToolWindowAnchor.RIGHT));
        factory.createToolWindowContent(project, window);
        Disposer.register(disposable, () -> {
            window.getContentManager().removeAllContents(true);
            manager.unregisterToolWindow(id);
        });
    }

    private DiagramDriver open(String name) {
        return DiagramDriver.open(myFixture, DrawioMappingTest.example(name));
    }

    @Test
    public void theFileTypeIsDrawioByExtension() {
        assertSame(DrawioFileType.INSTANCE, FileTypeManager.getInstance().getFileTypeByExtension("drawio"));
        assertEquals("draw.io Diagram", DrawioFileType.INSTANCE.getName());
        assertFalse(DrawioFileType.INSTANCE.isBinary());
    }

    @Test
    public void everyExampleOpensInTheDesigner() {
        for (String name : DrawioMappingTest.EXAMPLES) {
            try (var d = open(name)) {
                assertEquals(name, DrawioEditorProvider.EDITOR_TYPE_ID, d.driver().editorTypeIdUsed());
                assertNotNull(name, d.designer());
                assertFalse(name + ": " + d.designer().problemMessage(), d.driver().problemShown());
                assertFalse(name, d.elementKeys().isEmpty());
                assertFalse(name, d.driver().isModified());
                assertEquals("draw.io Designer", d.driver().composite().getName());
            }
        }
    }

    @Test
    public void onlyDrawioFilesThatSniffAreClaimed() {
        try (var d = DiagramDriver.openText(myFixture, "diagram.xml", DrawioMappingTest.read("flowchart_1"))) {
            assertNull("not a .drawio file", d.designer());
            assertFalse(d.driver().editorTypeIdsOffered().contains(DrawioEditorProvider.EDITOR_TYPE_ID));
        }
        try (var d = DiagramDriver.openText(myFixture, "picture.drawio", "<svg xmlns=\"http://www.w3.org/2000/svg\"/>")) {
            assertNull("not draw.io content", d.designer());
            assertFalse(d.driver().editorTypeIdsOffered().contains(DrawioEditorProvider.EDITOR_TYPE_ID));
        }
        try (var d = DiagramDriver.openText(myFixture, "empty.drawio", "")) {
            assertNull(d.designer());
        }
    }

    @Test
    public void theEditingScriptUndoesToTheSameBytes() throws Exception {
        try (var d = open("flowchart_1")) {
            byte[] opened = Files.readAllBytes(DrawioMappingTest.example("flowchart_1"));

            d.dragFromToolbox("rounded", 300, 1500);
            assertEquals("Undo Add Rounded Rectangle", d.driver().undoLabel());
            assertTrue(d.elementKeys().contains("adp-1"));
            d.driver().select();
            d.connect(DrawioDefinition.EDGE, "90", "x0.5y1", "adp-1", "x0.5y0");
            assertEquals("Undo Connect Connector", d.driver().undoLabel());
            assertTrue(d.connectionKeys().contains("adp-2"));
            d.moveBy(0, 20, "90");
            assertEquals("Undo Move", d.driver().undoLabel());
            d.driver().select("92").press("DELETE");
            assertEquals("Undo Delete", d.driver().undoLabel());
            assertFalse(d.elementKeys().contains("92"));
            assertFalse("its edge went with it", d.connectionKeys().contains("89"));
            assertTrue(d.driver().isModified());

            for (int i = 0; i < 4; i++) {
                d.driver().undo();
            }

            assertEquals(new String(opened, UTF_8), d.driver().text());
            assertFalse(d.driver().isModified());
            assertArrayEquals(opened, d.driver().savedBytes());
        }
    }

    @Test
    public void thePropertyPanelChangesFillColourAndEdgeStyle() {
        try (var d = open("flowchart_1")) {
            String before = d.driver().text();
            d.driver().select("90");
            assertEquals("#23445D", d.properties().row("fillColor").value());
            assertTrue("the id is read-only", d.properties().row("id").readOnly());
            d.setProperty("fillColor", "#FF0000");
            assertEquals(before.replace("style=\"rounded=1;fillColor=#23445D;strokeColor=none;strokeWidth=2;fontFamily=Helvetica;html=1;gradientColor=none;\" parent=\"1\" vertex=\"1\">\n          <mxGeometry x=\"270.3945578231293\"",
                    "style=\"rounded=1;fillColor=#FF0000;strokeColor=none;strokeWidth=2;fontFamily=Helvetica;html=1;gradientColor=none;\" parent=\"1\" vertex=\"1\">\n          <mxGeometry x=\"270.3945578231293\""),
                    d.driver().text());

            d.driver().select("89");
            assertEquals("orthogonal", d.properties().row("edgeStyle").value());
            d.setProperty("edgeStyle", "curved");
            String edge = d.driver().text().lines().filter(line -> line.contains("<mxCell id=\"89\"")).findFirst().orElseThrow();
            assertTrue(edge, edge.contains("curved=1"));
            assertFalse(edge, edge.contains("edgeStyle="));

            d.driver().undo();
            d.driver().undo();
            assertEquals(before, d.driver().text());
        }
    }

    @Test
    public void edgesArePaintedAndHitAboveTheGridRectanglesUnderThem() {
        try (var d = open("flowchart_1")) {
            assertTrue("the grid rectangle is under the point", d.elementView("68").bounds().contains(422, 1000));
            assertEquals("the edge through it is on top", "89", d.designer().canvas().itemAt(new Point2D.Double(422, 1000)));
            assertEquals("the milestones show, with their text", "Milestone 1", d.elementView("141").texts().get("label"));
            assertNull("the group itself is not drawn", d.elementView("140"));
        }
    }

    @Test
    public void aListMovesWithItsRowsWhichAreSelectableButNotMovable() throws Exception {
        try (var d = open("data_flow_1")) {
            String list = "21ea969265ad0168-14";
            String row = "21ea969265ad0168-17";
            String before = d.driver().text();
            d.moveBy(0, 20, row);
            assertEquals("a row does not move on its own", before, d.driver().text());
            d.driver().select(row);
            assertEquals("Row 3", d.properties().row("label").value());
            assertEquals(List.of("x0y0.5", "x1y0.5"), d.anchorsOf(row).stream().map(a -> a.id()).toList());

            java.awt.Rectangle rowBefore = d.elementView(row).bounds();
            java.awt.geom.Rectangle2D at = d.designer().diagram().element(list).bounds();
            at.setRect(at.getX() + 40, at.getY(), at.getWidth(), at.getHeight());
            assertTrue("moved by its header, as in draw.io", d.designer().commands().move(java.util.Map.of(list, at)).allowed());
            d.driver().settle();
            assertEquals("Undo Move", d.driver().undoLabel());
            assertEquals("the row moved with its list", rowBefore.x + 40, d.elementView(row).bounds().x);
            assertNotNull("its edge is still drawn", d.connectionView("21ea969265ad0168-33"));
            d.driver().undo();
            assertEquals(before, d.driver().text());
        }
    }

    @Test
    public void aCompressedFileOpensInTheTextViewWithTheExplanation() {
        try (var d = open("compressed")) {
            assertEquals(DrawioEditorProvider.EDITOR_TYPE_ID, d.driver().editorTypeIdUsed());
            assertTrue(d.driver().problemShown());
            assertEquals(Layout.TEXT, d.driver().layout());
            assertTrue(d.designer().problemMessage(), d.designer().problemMessage().contains("\"Compressed\""));
            assertEquals(List.of(), d.elementKeys());
            assertFalse(d.driver().isModified());
        }
    }
}
