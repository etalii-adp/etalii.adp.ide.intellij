package etalii.adp.testing;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTable;
import javax.swing.text.JTextComponent;

import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.ui.ColorPanel;
import com.intellij.ui.content.Content;

import etalii.adp.core.diagram.Anchor;
import etalii.adp.core.diagram.DiagramChange;
import etalii.adp.core.diagram.EditorKind;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.Orientation;
import etalii.adp.core.diagram.SectorDecl;
import etalii.adp.core.diagram.Space;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.model.Sector;
import etalii.adp.core.diagram.view.AnchorGeometry;
import etalii.adp.core.diagram.view.AnchorView;
import etalii.adp.core.diagram.view.ConnectionView;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;
import etalii.adp.core.diagram.view.ElementView;
import etalii.adp.core.diagram.view.Handle;
import etalii.adp.core.diagram.view.PropertiesContent;
import etalii.adp.core.diagram.view.ToolboxContent;

/**
 * Drives a diagram designer as a user would, inside a headless IDE (contracts/test-kit.md). It
 * wraps a {@link DesignerDriver} for everything that is not diagram-specific. Positions are in
 * unzoomed diagram coordinates; the driver converts them to the canvas at the current zoom and
 * dispatches real mouse events to it, so the canvas's own tools run. The toolbox and property
 * panel methods use the real tool window content, found by tool window id.
 */
public final class DiagramDriver implements AutoCloseable {

    public static final String TOOLBOX = "ADP Toolbox";
    public static final String PROPERTIES = "ADP Properties";

    private static final int DRAG_STEPS = 4;

    private final DesignerDriver driver;

    private DiagramDriver(DesignerDriver driver) {
        this.driver = driver;
    }

    /** Copy a file into the test project, byte for byte, and open it as the IDE would. */
    public static DiagramDriver open(CodeInsightTestFixture fixture, Path file) {
        return new DiagramDriver(DesignerDriver.open(fixture, file));
    }

    /** Same, for an in-memory text, written as UTF-8. */
    public static DiagramDriver openText(CodeInsightTestFixture fixture, String fileName, String content) {
        return new DiagramDriver(DesignerDriver.openText(fixture, fileName, content));
    }

    /** The wrapped driver: select, undo, redo, text, save and the rest. */
    public DesignerDriver driver() {
        return driver;
    }

    /** The diagram designer, or {@code null} when the IDE opened another editor. */
    public DiagramDesigner designer() {
        return driver.designer() instanceof DiagramDesigner designer ? designer : null;
    }

    // Observing the diagram

    /** How an element is drawn, or {@code null} when it is not shown. */
    public ElementView elementView(Object key) {
        return designer().elementView(key);
    }

    public ConnectionView connectionView(Object key) {
        return designer().connectionView(key);
    }

    /** The shown elements, in painting order. */
    public List<Object> elementKeys() {
        return designer().elementKeys();
    }

    public List<Object> connectionKeys() {
        return designer().connectionKeys();
    }

    /** The resize handles offered for a selected element. */
    public List<Handle> handlesOf(Object key) {
        return designer().handlesOf(key);
    }

    public List<AnchorView> anchorsOf(Object key) {
        return designer().anchorsOf(key);
    }

    /** The reason of the last refused gesture, or {@code null}. */
    public String refusal() {
        Verdict refusal = designer().lastRefusal();
        return refusal == null ? null : refusal.reason();
    }

    /** What the designer's listener received last. */
    public List<DiagramChange> lastChanges() {
        return designer().lastChanges();
    }

    // Acting on the canvas

    /** Select the elements and drag the first by {@code dx}, {@code dy}. */
    public DesignerDriver moveBy(double dx, double dy, Object... keys) {
        designer().select(List.of(keys));
        driver.settle();
        Point2D from = centre(bounds(keys[0]));
        drag(from, new Point2D.Double(from.getX() + dx, from.getY() + dy), 0);
        return driver;
    }

    /** Select the element and drag one of its resize handles by {@code dx}, {@code dy}. */
    public DesignerDriver resize(Object key, Handle handle, double dx, double dy) {
        designer().select(List.of(key));
        driver.settle();
        Point2D from = handle.at(bounds(key));
        drag(from, new Point2D.Double(from.getX() + dx, from.getY() + dy), 0);
        return driver;
    }

    /** Drag from one anchor to another, with the armed connection type or the first the anchor accepts. */
    public DesignerDriver connect(Object fromKey, String fromAnchor, Object toKey, String toAnchor) {
        Point2D from = anchor(fromKey, fromAnchor);
        Point2D to = anchor(toKey, toAnchor);
        drag(grip(fromKey, fromAnchor, from, to), grip(toKey, toAnchor, to, from), 0);
        return driver;
    }

    /** The same with {@code connectionType} armed, as choosing it in the toolbox does. */
    public DesignerDriver connect(String connectionType, Object fromKey, String fromAnchor, Object toKey, String toAnchor) {
        String armed = designer().armedConnectionType();
        designer().armConnectionType(connectionType);
        try {
            return connect(fromKey, fromAnchor, toKey, toAnchor);
        } finally {
            designer().armConnectionType(armed);
        }
    }

    /** Drag the element across its sector's bands into another sector: along y for horizontal bands, along x for vertical ones. */
    public DesignerDriver dragToSector(Object key, Object sectorKey) {
        DiagramDesigner designer = designer();
        Sector sector = designer.diagram().sector(sectorKey);
        if (sector == null) {
            throw new IllegalArgumentException("No sector " + sectorKey);
        }
        SectorDecl decl = designer.definition().sectors().get(sector.declId());
        Rectangle2D area = sector.bounds();
        Point2D centre = new Point2D.Double(area.getCenterX(), area.getCenterY());
        if (decl != null && decl.space() == Space.VIEW) {
            Point origin = canvas().viewportPosition();
            double zoom = canvas().zoom();
            centre = new Point2D.Double((origin.x + centre.getX()) / zoom, (origin.y + centre.getY()) / zoom);
        }
        designer.select(List.of(key));
        driver.settle();
        Point2D from = centre(bounds(key));
        boolean horizontal = decl == null || decl.orientation() == Orientation.HORIZONTAL;
        Point2D to = horizontal ? new Point2D.Double(from.getX(), centre.getY()) : new Point2D.Double(centre.getX(), from.getY());
        drag(from, to, 0);
        return driver;
    }

    /** Double-click an element's text slot, or a connection's label by slot name or property. */
    public DesignerDriver doubleClickText(Object key, String slotOrLabel) {
        Rectangle2D box = designer().textBounds(key, slotOrLabel);
        if (box == null) {
            throw new IllegalArgumentException(key + " shows no text " + slotOrLabel);
        }
        Point at = canvas().toCanvas(centre(box));
        for (int count = 1; count <= 2; count++) {
            mouse(MouseEvent.MOUSE_PRESSED, at, count, InputEvent.BUTTON1_DOWN_MASK);
            mouse(MouseEvent.MOUSE_RELEASED, at, count, 0);
            mouse(MouseEvent.MOUSE_CLICKED, at, count, 0);
        }
        driver.settle();
        return driver;
    }

    /** True while an in-place editor is open on the canvas. */
    public boolean inPlaceEditing() {
        return find(canvas(), JTextComponent.class) != null;
    }

    /** Drag a selection rectangle from one diagram point to another. */
    public DesignerDriver marquee(double x1, double y1, double x2, double y2) {
        drag(new Point2D.Double(x1, y1), new Point2D.Double(x2, y2), 0);
        return driver;
    }

    /** Ctrl+wheel over the middle of the visible canvas; negative steps zoom out. */
    public DesignerDriver zoom(int steps) {
        DiagramCanvas canvas = canvas();
        Rectangle visible = canvas.getVisibleRect();
        Point at = visible.isEmpty() ? new Point(canvas.getWidth() / 2, canvas.getHeight() / 2)
                : new Point(visible.x + visible.width / 2, visible.y + visible.height / 2);
        for (int i = 0; i < Math.abs(steps); i++) {
            canvas.dispatchEvent(new MouseWheelEvent(canvas, MouseEvent.MOUSE_WHEEL, System.currentTimeMillis(), InputEvent.CTRL_DOWN_MASK, at.x, at.y, 0,
                    false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 1, steps > 0 ? -1 : 1));
            driver.settle();
        }
        return driver;
    }

    public double zoomLevel() {
        return designer().viewState().zoom();
    }

    // Toolbox

    /** The type ids the toolbox lists, in its order. */
    public List<String> toolboxEntries() {
        return toolbox().entries();
    }

    /** Drag a toolbox entry onto the canvas at a diagram point. */
    public DesignerDriver dragFromToolbox(String typeId, double x, double y) {
        toolbox().dragTo(typeId, canvas(), canvas().toCanvas(new Point2D.Double(x, y)));
        driver.settle();
        return driver;
    }

    /** Drag a toolbox entry onto an element. */
    public DesignerDriver dragFromToolboxOnto(String typeId, Object targetKey) {
        toolbox().dragTo(typeId, canvas(), canvas().toCanvas(centre(bounds(targetKey))));
        driver.settle();
        return driver;
    }

    /** Enter on a toolbox entry. */
    public DesignerDriver addFromToolboxWithKeyboard(String typeId) {
        toolbox().activate(typeId);
        driver.settle();
        return driver;
    }

    // Property panel

    /** The panel's rows for the current selection. */
    public PropertyRows properties() {
        driver.settle();
        PropertiesContent content = properties(PROPERTIES);
        JTable table = content.table();
        List<PropertyRow> rows = new ArrayList<>();
        for (int row = 0; row < table.getRowCount(); row++) {
            String id = content.propertyId(row);
            if (id != null) {
                rows.add(new PropertyRow(id, String.valueOf(table.getValueAt(row, 0)), text(table.getValueAt(row, 1)), content.editor(row),
                        !table.isCellEditable(row, 1), content.mixed(row)));
            }
        }
        return new PropertyRows(rows);
    }

    /** Edit a property through its cell editor, then commit, as a user does. */
    public DesignerDriver setProperty(String propertyId, String value) {
        driver.settle();
        PropertiesContent content = properties(PROPERTIES);
        JTable table = content.table();
        int row = -1;
        for (int i = 0; i < table.getRowCount() && row < 0; i++) {
            if (propertyId.equals(content.propertyId(i))) {
                row = i;
            }
        }
        if (row < 0) {
            throw new IllegalArgumentException("The panel shows no property " + propertyId);
        }
        if (!table.editCellAt(row, 1)) {
            throw new IllegalStateException("Property " + propertyId + " cannot be edited");
        }
        enter(table.getEditorComponent(), value);
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
        driver.settle();
        return driver;
    }

    /** The panel's rows. */
    public record PropertyRows(List<PropertyRow> rows) {

        public PropertyRows {
            rows = List.copyOf(rows);
        }

        /** The row of a property, or {@code null}. */
        public PropertyRow row(String propertyId) {
            return rows.stream().filter(row -> row.id().equals(propertyId)).findFirst().orElse(null);
        }
    }

    /** One property row: its value as the panel shows it, empty when mixed. */
    public record PropertyRow(String id, String label, String value, EditorKind editor, boolean readOnly, boolean mixed) {
    }

    /** Close the editor without saving. */
    @Override
    public void close() {
        driver.close();
    }

    private DiagramCanvas canvas() {
        return designer().canvas();
    }

    private Rectangle2D bounds(Object key) {
        ElementView view = elementView(key);
        if (view == null) {
            throw new IllegalArgumentException("No element " + key + " is shown");
        }
        return view.bounds();
    }

    private Point2D anchor(Object key, String anchorId) {
        return anchorsOf(key).stream().filter(a -> a.id().equals(anchorId)).findFirst().map(AnchorView::position)
                .orElseThrow(() -> new IllegalArgumentException(key + " has no anchor " + anchorId));
    }

    /** Where a user takes hold of an anchor: its point, or for a perimeter anchor the outline toward the other end. */
    private Point2D grip(Object key, String anchorId, Point2D at, Point2D toward) {
        ElementView view = elementView(key);
        ElementType type = designer().definition().elementType(view.type());
        Anchor anchor = type == null ? null : type.anchor(anchorId);
        return anchor == null || !anchor.perimeter() ? at : AnchorGeometry.perimeter(view.outline().shape(view.bounds()), at, toward);
    }

    private static Point2D centre(Rectangle2D box) {
        return new Point2D.Double(box.getCenterX(), box.getCenterY());
    }

    /** Press at {@code from}, drag in steps and release at {@code to}, all in diagram coordinates. */
    private void drag(Point2D from, Point2D to, int modifiers) {
        DiagramCanvas canvas = canvas();
        Point start = canvas.toCanvas(from);
        Point end = canvas.toCanvas(to);
        mouse(MouseEvent.MOUSE_PRESSED, start, 1, InputEvent.BUTTON1_DOWN_MASK | modifiers);
        for (int step = 1; step <= DRAG_STEPS; step++) {
            Point at = new Point(start.x + (end.x - start.x) * step / DRAG_STEPS, start.y + (end.y - start.y) * step / DRAG_STEPS);
            mouse(MouseEvent.MOUSE_DRAGGED, at, 0, InputEvent.BUTTON1_DOWN_MASK | modifiers);
        }
        mouse(MouseEvent.MOUSE_RELEASED, end, 1, modifiers);
        driver.settle();
    }

    private void mouse(int id, Point at, int clickCount, int modifiers) {
        DiagramCanvas canvas = canvas();
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        canvas.dispatchEvent(new MouseEvent(canvas, id, System.currentTimeMillis(), modifiers, at.x, at.y, clickCount, false, button));
    }

    private ToolboxContent toolbox() {
        return content(TOOLBOX, ToolboxContent.class);
    }

    private PropertiesContent properties(String id) {
        return content(id, PropertiesContent.class);
    }

    private <T> T content(String toolWindowId, Class<T> type) {
        ToolWindow window = ToolWindowManager.getInstance(designer().project()).getToolWindow(toolWindowId);
        if (window == null) {
            throw new IllegalStateException("No tool window " + toolWindowId);
        }
        for (Content content : window.getContentManager().getContents()) {
            T found = find(content.getComponent(), type);
            if (found != null) {
                return found;
            }
        }
        throw new IllegalStateException("Tool window " + toolWindowId + " shows no " + type.getSimpleName());
    }

    /** Type a value into a cell editor: a colour panel, a combo box, a check box or a text field. */
    private static void enter(Component editor, String value) {
        ColorPanel colour = find(editor, ColorPanel.class);
        if (colour != null) {
            colour.setSelectedColor(value.isEmpty() ? null : Color.decode(value));
            return;
        }
        JComboBox<?> combo = find(editor, JComboBox.class);
        if (combo != null) {
            for (int i = 0; i < combo.getItemCount(); i++) {
                if (value.equals(text(combo.getItemAt(i)))) {
                    combo.setSelectedIndex(i);
                    return;
                }
            }
            throw new IllegalArgumentException("'" + value + "' is not a choice");
        }
        JCheckBox check = find(editor, JCheckBox.class);
        if (check != null) {
            check.setSelected(Boolean.parseBoolean(value));
            return;
        }
        JTextComponent text = find(editor, JTextComponent.class);
        if (text == null) {
            throw new IllegalStateException("No editor to type into: " + editor);
        }
        text.setText(value);
    }

    /** A cell value as the file's notation. */
    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof EditorKind.Option option) {
            return option.value();
        }
        return String.valueOf(value);
    }

    private static <T> T find(Component component, Class<T> type) {
        if (component == null) {
            return null;
        }
        if (type.isInstance(component) && component.isVisible()) {
            return type.cast(component);
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                T found = find(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
