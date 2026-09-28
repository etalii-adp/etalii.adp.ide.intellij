package etalii.adp.core.diagram.properties;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.swing.JTable;
import javax.swing.text.JTextComponent;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.RegisterToolWindowTask;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.ui.content.Content;

import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.EditorKind;
import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleMapping;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.toolbox.ToolboxTest;
import etalii.adp.core.diagram.view.DiagramEditorProvider;
import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.DiagramDriver;
import etalii.adp.testing.DiagramDriver.PropertyRow;
import etalii.adp.testing.DiagramDriver.PropertyRows;

/**
 * T069, US3-1, US3-3, US3-4 and US3-5, FR-021 to FR-024: the ADP Properties tool window shows the
 * declarations the selection shares, grouped by category, with read-only rows that refuse edits.
 * Every editor kind round-trips its value as one undo step; input that does not fit its kind is
 * refused before any edit; mixed values show empty with a hint; the panel follows the selected
 * editor and clears when its item is removed, even with a cell editor open.
 */
@RunWith(JUnit4.class)
public class PropertyPanelTest extends FileEditorManagerTestCase {

    /** Three tasks: a and b share their priority, not their owner; d is a decision. */
    static final String THREE = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sample>
              <box id="a" type="task" x="40" y="40" owner="Ann" priority="high">Place order</box>
              <box id="b" type="task" x="40" y="240" owner="Ben" priority="high">Pack</box>
              <box id="d" type="decision" x="440" y="40">Paid?</box>
            </sample>
            """;

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable());
        ToolboxTest.install(getProject(), getTestRootDisposable());
        install(getProject(), getTestRootDisposable());
    }

    /**
     * Register ADP Properties and fill it with its factory, as the IDE does from the descriptor. The
     * headless tool window manager does not create tool windows from descriptors.
     */
    public static ToolWindow install(Project project, Disposable disposable) {
        ToolWindowManager manager = ToolWindowManager.getInstance(project);
        ToolWindow window = manager.registerToolWindow(RegisterToolWindowTask.notClosable(PropertiesToolWindowFactory.ID, ToolWindowAnchor.RIGHT));
        new PropertiesToolWindowFactory().createToolWindowContent(project, window);
        Disposer.register(disposable, () -> {
            window.getContentManager().removeAllContents(true);
            manager.unregisterToolWindow(PropertiesToolWindowFactory.ID);
        });
        return window;
    }

    private DiagramDriver open(String name) {
        return followed(DiagramDriver.open(myFixture, SampleFiles.directory().resolve(name)));
    }

    private DiagramDriver three() {
        return followed(DiagramDriver.openText(myFixture, "three.adpsample", THREE));
    }

    /** The panel follows a newly opened designer after the queue first drains, so wait for it before reading rows. */
    private DiagramDriver followed(DiagramDriver d) {
        try {
            d.driver().settleUntil("the panel follows the newly opened designer", () -> panel().designer() == d.designer());
        } catch (RuntimeException | AssertionError e) {
            d.close();
            throw e;
        }
        return d;
    }

    private PropertyPanel panel() {
        ToolWindow window = ToolWindowManager.getInstance(getProject()).getToolWindow(PropertiesToolWindowFactory.ID);
        for (Content content : window.getContentManager().getContents()) {
            if (content.getComponent() instanceof PropertyPanel panel) {
                return panel;
            }
        }
        throw new AssertionError("ADP Properties shows no property panel");
    }

    private static List<String> ids(PropertyRows rows) {
        return rows.rows().stream().map(PropertyRow::id).toList();
    }

    private boolean hasRow(String propertyId) {
        JTable table = panel().table();
        for (int row = 0; row < table.getRowCount(); row++) {
            if (propertyId.equals(panel().propertyId(row))) {
                return true;
            }
        }
        return false;
    }

    private int rowOf(String propertyId) {
        JTable table = panel().table();
        for (int row = 0; row < table.getRowCount(); row++) {
            if (propertyId.equals(panel().propertyId(row))) {
                return row;
            }
        }
        throw new AssertionError("no row for " + propertyId);
    }

    @Test
    public void aTitleIsEditableAndAnIdentifierIsNot() {
        try (var d = open("two-tasks.adpsample")) {
            d.driver().select("a");
            PropertyRows rows = d.properties();
            assertEquals("declared order, General before Planning", List.of("title", "owner", "priority", "done", "color", "id", "estimate"), ids(rows));

            PropertyRow title = rows.row("title");
            assertEquals("Title", title.label());
            assertEquals("Place order", title.value());
            assertEquals(EditorKind.MULTILINE, title.editor());
            assertFalse(title.readOnly());
            assertFalse(title.mixed());
            PropertyRow id = rows.row("id");
            assertEquals("a", id.value());
            assertTrue("the identifier is read-only", id.readOnly());
            assertEquals("Ann", rows.row("owner").value());
            assertEquals("high", rows.row("priority").value());
            assertEquals("", rows.row("done").value());

            List<String> categories = new ArrayList<>();
            JTable table = panel().table();
            for (int row = 0; row < table.getRowCount(); row++) {
                if (panel().propertyId(row) == null) {
                    categories.add(String.valueOf(table.getValueAt(row, 0)));
                }
            }
            assertEquals("rows are grouped under their categories", List.of("General", "Planning"), categories);

            String before = d.driver().text();
            try {
                d.setProperty("id", "x");
                fail("the identifier cannot be edited");
            } catch (IllegalStateException expected) {
                // refused by the table: no cell editor opens
            }
            assertEquals(before, d.driver().text());

            d.setProperty("title", "Order goods");
            assertEquals(before.replace(">Place order<", ">Order goods<"), d.driver().text());
            assertEquals("Undo Change Title", d.driver().undoLabel());
            assertEquals("Order goods", d.properties().row("title").value());
            assertEquals("the canvas shows it", "Order goods", d.elementView("a").texts().get("title"));
        }
    }

    @Test
    public void eachEditorKindRoundTripsItsValueAsOneUndoStep() {
        roundTrip("owner", "Bob", "owner=\"Bob\"");
        roundTrip("title", "Line one\nLine two", ">Line one\nLine two</box>");
        roundTrip("priority", "low", "priority=\"low\"");
        roundTrip("done", "true", "done=\"true\"");
        roundTrip("estimate", "42", "estimate=\"42\"");
        roundTrip("color", "#336699", "color=\"#336699\"");
    }

    private void roundTrip(String property, String value, String expected) {
        try (var d = DiagramDriver.openText(myFixture, property + ".adpsample", SampleFiles.read("two-tasks.adpsample"))) {
            d.driver().select("a");
            d.driver().settleUntil("the panel shows " + property + " for the new designer",
                    () -> panel().designer() == d.designer() && hasRow(property));
            String before = d.driver().text();
            String was = d.properties().row(property).value();

            d.setProperty(property, value);

            assertTrue(property + ": " + d.driver().text(), d.driver().text().contains(expected));
            assertEquals(property, value, d.properties().row(property).value());
            assertEquals(property, value, d.designer().diagram().element("a").property(property));
            assertEquals("Undo Change " + d.properties().row(property).label(), d.driver().undoLabel());

            d.driver().undo();
            assertEquals(property + ": one undo restores the text", before, d.driver().text());
            assertNull(property + ": one step", d.driver().undoLabel());
            assertEquals(property + ": the panel follows the undo", was, d.properties().row(property).value());
        }
    }

    @Test
    public void inputThatDoesNotFitItsKindIsRefusedBeforeAnyEdit() {
        try (var d = open("two-tasks.adpsample")) {
            d.driver().select("a");
            String before = d.driver().text();
            for (String bad : List.of("abc", "4.5", "500")) {
                d.setProperty("estimate", bad);
                assertEquals(bad + " changes nothing", before, d.driver().text());
                assertNull(bad + " is no step", d.driver().undoLabel());
                assertTrue(bad + " keeps the cell editor open", panel().table().isEditing());
                assertNotNull(bad + " gives a reason", panel().lastProblem());
                assertTrue(panel().lastProblem(), panel().lastProblem().contains(bad));
                panel().table().getCellEditor().cancelCellEditing();
            }
            d.setProperty("estimate", "100");
            assertTrue(d.driver().text().contains("estimate=\"100\""));
        }
    }

    @Test
    public void sharedValuesShowAndAnEditAppliesToAllAsOneStep() {
        try (var d = three()) {
            String before = d.driver().text();
            d.driver().select("a", "b");
            PropertyRows rows = d.properties();
            assertEquals(List.of("title", "owner", "priority", "done", "color", "id", "estimate"), ids(rows));
            assertEquals("high", rows.row("priority").value());
            assertFalse(rows.row("priority").mixed());
            assertTrue(rows.row("owner").mixed());
            assertEquals("a mixed value shows empty", "", rows.row("owner").value());
            assertTrue(rows.row("title").mixed());
            assertTrue(rows.row("id").readOnly());

            d.setProperty("owner", "Cy");
            assertEquals(before.replace("owner=\"Ann\"", "owner=\"Cy\"").replace("owner=\"Ben\"", "owner=\"Cy\""), d.driver().text());
            assertEquals("Undo Change Owner", d.driver().undoLabel());
            assertEquals(List.of("a", "b"), d.driver().selectedKeys());
            assertEquals("Cy", d.properties().row("owner").value());
            assertFalse(d.properties().row("owner").mixed());

            d.driver().undo();
            assertEquals("one undo restores both", before, d.driver().text());

            d.driver().select("a", "d");
            assertEquals("a task and a decision share only their title and id", List.of("title", "id"), ids(d.properties()));
        }
    }

    @Test
    public void aPlaceholderShowsItsKeyReadOnly() {
        try (var d = open("unknown-type.adpsample")) {
            d.driver().select("z");
            PropertyRows rows = d.properties();
            assertEquals(List.of(PropertyTableModel.KEY), ids(rows));
            assertEquals("z", rows.row(PropertyTableModel.KEY).value());
            assertTrue(rows.row(PropertyTableModel.KEY).readOnly());

            d.driver().select("w1");
            assertEquals("w1", d.properties().row(PropertyTableModel.KEY).value());
        }
    }

    @Test
    public void showsItsEmptyStates() {
        assertEquals(List.of(), ids(new PropertyRows(List.of())));
        assertEquals(0, panel().table().getRowCount());
        assertEquals("No ADP designer is active", panel().emptyText());
        assertEquals(PropertyPanel.NO_DESIGNER, panel().emptyText());
        try (var d = open("two-tasks.adpsample")) {
            assertEquals(PropertyPanel.NOTHING_SELECTED, panel().emptyText());
            assertEquals("Nothing selected", PropertyPanel.NOTHING_SELECTED);
            assertEquals(List.of(), ids(d.properties()));
            d.driver().select("f1");
            assertEquals("a connection's properties", List.of("label", "tag", "note", "id"), ids(d.properties()));
            assertEquals("submit", d.properties().row("label").value());
            d.driver().select();
            assertEquals(List.of(), ids(d.properties()));
            assertEquals(PropertyPanel.NOTHING_SELECTED, panel().emptyText());
        }
        panel().refreshFromSelectedEditor();
        assertEquals(PropertyPanel.NO_DESIGNER, panel().emptyText());
    }

    @Test
    public void clearsWhenItsItemIsRemovedEvenWithACellEditorOpen() {
        try (var d = open("two-tasks.adpsample")) {
            d.driver().select("a");
            JTable table = panel().table();
            assertTrue(table.editCellAt(rowOf("owner"), 1));
            ((JTextComponent) findText(table.getEditorComponent())).setText("Zed");

            d.driver().editText(text -> text.replaceAll("  <box id=\"a\"[^\n]*\n", "").replaceAll("  <link[^\n]*\n", ""));

            assertFalse("the cell editor is cancelled", table.isEditing());
            assertEquals(List.of(), ids(d.properties()));
            assertEquals(PropertyPanel.NOTHING_SELECTED, panel().emptyText());
            assertFalse("nothing stale is applied", d.driver().text().contains("Zed"));
            assertEquals("Undo Typing", d.driver().undoLabel());
        }
    }

    @Test
    public void followsTheSelectedEditorToAnotherDesigner() {
        GadgetProvider.register(getTestRootDisposable());
        try (var d = open("unknown-type.adpsample")) {
            d.driver().select("z");
            assertEquals("a placeholder in the sample designer", List.of(PropertyTableModel.KEY), ids(d.properties()));
            assertSame(d.designer(), panel().designer());

            try (var other = DiagramDriver.openText(myFixture, "gadgets.adpgadget", SampleFiles.read("unknown-type.adpsample"))) {
                other.driver().settleUntil("the panel follows the gadget designer", () -> panel().designer() == other.designer());
                assertSame(other.designer(), panel().designer());
                assertEquals(PropertyPanel.NOTHING_SELECTED, panel().emptyText());
                other.driver().select("z");
                PropertyRows rows = other.properties();
                assertEquals("the gadget designer's own declarations", List.of("title", "colour", "id"), ids(rows));
                assertEquals("teal", rows.row("colour").value());
                assertEquals("Colour name", rows.row("colour").label());

                other.setProperty("colour", "red");
                assertTrue(other.driver().text().contains("colour=\"red\""));
                assertEquals("Undo Change Colour name", other.driver().undoLabel());
            }

            VirtualFile notes = DesignerDriver.createFile(myFixture, "notes.txt", "plain text\n".getBytes(UTF_8));
            FileEditorManager editors = FileEditorManager.getInstance(getProject());
            editors.openFile(notes, true);
            d.driver().settleUntil("the panel leaves the designer for a text file", () -> panel().designer() == null);
            assertNull(panel().designer());
            assertEquals(PropertyPanel.NO_DESIGNER, panel().emptyText());

            editors.openFile(d.driver().file(), true);
            d.driver().settleUntil("the panel follows back to the sample designer", () -> panel().designer() == d.designer());
            assertSame(d.designer(), panel().designer());
            assertEquals(List.of(PropertyTableModel.KEY), ids(d.properties()));
            editors.closeFile(notes);
        }
    }

    private static java.awt.Component findText(java.awt.Component component) {
        if (component instanceof JTextComponent) {
            return component;
        }
        if (component instanceof java.awt.Container container) {
            for (java.awt.Component child : container.getComponents()) {
                java.awt.Component found = findText(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    /** A second designer that declares the sample file's unknown {@code gizmo} type, with properties of its own. */
    public static final class GadgetProvider extends DiagramEditorProvider {

        GadgetProvider() {
            super(DiagramDefinition.builder("gadgets")
                    .element("gizmo", e -> e.label("Gizmo")
                            .text("title", t -> t.property("title").editable(true))
                            .property("title", p -> p.label("Title"))
                            .property("colour", p -> p.label("Colour name"))
                            .property("id", p -> p.label("Id").readOnly(true))),
                    SampleMapping::new);
        }

        static void register(Disposable disposable) {
            FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new GadgetProvider(), disposable);
        }

        @Override
        protected Set<String> extensions() {
            return Set.of("adpgadget");
        }

        @Override
        protected boolean sniff(byte[] head) {
            return new String(head, UTF_8).contains("<sample");
        }

        @Override
        protected String editorName() {
            return "Gadget Designer";
        }

        @Override
        public String getEditorTypeId() {
            return "etalii.adp.sample.gadgets";
        }
    }
}
