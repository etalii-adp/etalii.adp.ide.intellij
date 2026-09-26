package etalii.adp.core.diagram.view;

import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.font.FontRenderContext;
import java.awt.geom.Dimension2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.swing.JComponent;

import org.jetbrains.annotations.NotNull;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.AdpDesignerEditor;
import etalii.adp.core.FormatProblem;
import etalii.adp.core.NodeView;
import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.Anchor;
import etalii.adp.core.diagram.ConnectionType;
import etalii.adp.core.diagram.DiagramChange;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.DiagramFeature;
import etalii.adp.core.diagram.DiagramLayout;
import etalii.adp.core.diagram.DiagramMapping;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.LabelSlot;
import etalii.adp.core.diagram.LineStyle;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.edit.DiagramCommands;
import etalii.adp.core.diagram.edit.DiagramDiff;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.view.ElementMeasure.PlacedText;
import etalii.adp.core.diagram.view.Scene.ConnectionRender;
import etalii.adp.core.diagram.view.Scene.ElementRender;
import etalii.adp.core.diagram.view.Scene.PlacedLabel;

/**
 * A diagram designer made of a {@link DiagramDefinition} and a {@link DiagramMapping} (FR-001,
 * research R5 to R8): it reads the document through the mapping, lays it out from the file's
 * bounds, the designer's {@link DiagramLayout} or measurement, routes the connections and shows
 * the result on a {@link DiagramCanvas}. After each re-read it diffs with the diagram shown before
 * and tells the definition's listener what changed, so edits, undo, redo and external changes take
 * one path. Every registered {@link DiagramFeature} adds its tools and layers to the canvas.
 * <p>
 * Its {@link #model()} is the diagram itself, unless a subclass parses the text into a model of its
 * own and shows it as a diagram through {@link #diagramOf} (FreeMind's map, research R19). The
 * framework works on {@link #diagram()} only.
 */
public class DiagramDesigner extends AdpDesignerEditor<Object> implements DiagramCommands.Host {

    /** The canvas context menu; the editing fragment registers it, and until then there is none. */
    public static final String POPUP_GROUP = "etalii.adp.core.DiagramPopup";

    private static final int ROUTE_MARGIN = 10;

    /** Which way {@link #navigate} moves from an element. */
    public enum Heading {
        UP, DOWN, LEFT, RIGHT
    }

    private final DiagramDefinition definition;
    private final DiagramMapping mapping;
    private final DiagramCommands commands;
    private final Map<Object, ElementRender> elementCache = new HashMap<>();
    private final Map<Object, Element> elementSource = new HashMap<>();
    private final Map<Object, Color> elementUnder = new HashMap<>();
    private final Map<Object, Dimension2D> sizeCache = new HashMap<>();
    private final Map<Object, Connection> connectionSource = new HashMap<>();
    private Router router = new Router(JBUI.scale(ROUTE_MARGIN));
    private DiagramCanvas canvas;
    private ElementMeasure measure;
    private Diagram shown;
    private List<DiagramChange> lastChanges = List.of();
    private String armedConnectionType;

    public DiagramDesigner(Project project, VirtualFile file, Document document, DiagramDefinition definition, DiagramMapping mapping) {
        super(project, file, document);
        this.definition = definition;
        this.mapping = mapping;
        this.commands = new DiagramCommands(this);
    }

    /** The diagram, read through the mapping; a subclass may return a model of its own and override {@link #diagramOf}. */
    @Override
    protected Object parse(CharSequence text) throws FormatProblem {
        return mapping.read(text);
    }

    /** The diagram a parsed model shows: the model itself, unless a subclass keeps its own. Called often, so a subclass caches it. */
    protected Diagram diagramOf(Object model) {
        return (Diagram) model;
    }

    /** The canvas the diagram is drawn on; a subclass may return its own, for what only it shows. */
    protected DiagramCanvas createCanvas() {
        return new DiagramCanvas(this);
    }

    @Override
    protected JComponent createView() {
        canvas = createCanvas();
        measure = new ElementMeasure(fontRenderContext());
        canvas.addTool(new ArrowKeys());
        viewState().addSelectionListener(canvas::repaint);
        if (ApplicationManager.getApplication().getExtensionArea().hasExtensionPoint(DiagramFeature.EP_NAME)) {
            for (DiagramFeature feature : DiagramFeature.EP_NAME.getExtensionList()) {
                feature.install(this, canvas, this);
            }
        }
        installActions(canvas, POPUP_GROUP);
        return canvas;
    }

    private FontRenderContext fontRenderContext() {
        return canvas.getFontMetrics(JBFont.label()).getFontRenderContext();
    }

    @Override
    protected void modelChanged(Object model) {
        Diagram diagram = diagramOf(model);
        List<Object> kept = new ArrayList<>();
        for (Object key : selection()) {
            if (diagram.element(key) != null || diagram.connection(key) != null) {
                kept.add(key);
            }
        }
        if (kept.size() != selection().size()) {
            viewState().select(kept);
        }
        layOut(diagram);
        List<DiagramChange> changes = shown == null ? List.of() : DiagramDiff.diff(shown, diagram);
        shown = diagram;
        lastChanges = changes;
        if (!changes.isEmpty()) {
            definition.listener().changed(this, changes);
        }
    }

    /** Measure, lay out and route everything again, as after a theme or scale change. */
    void relayoutAll() {
        if (measure == null) {
            return;
        }
        measure = new ElementMeasure(fontRenderContext());
        elementCache.clear();
        elementSource.clear();
        sizeCache.clear();
        connectionSource.clear();
        router = new Router(JBUI.scale(ROUTE_MARGIN));
        Diagram diagram = diagram();
        canvas.setScene(Scene.EMPTY);
        if (diagram != null) {
            layOut(diagram);
        }
    }

    /**
     * Lay out the diagram and route its connections into a new {@link Scene}. Elements and routes
     * whose inputs did not change since the last layout are reused, and only routes near an
     * element whose bounds changed are routed again (research R6, R12).
     */
    private void layOut(Diagram diagram) {
        Scene before = canvas.scene();
        Map<Object, Rectangle2D> bounds = bounds(diagram);
        Map<Object, ElementRender> elements = new LinkedHashMap<>();
        for (Element element : diagram.elements().values()) {
            Rectangle2D box = bounds.get(element.key());
            if (box == null) {
                continue;
            }
            // a part inside its parent, such as a list's row, is drawn on the parent's fill, so its texts are judged against that;
            // a mind map's child sits beside its parent, on the canvas
            ElementRender owner = element.parent() == null ? null : elements.get(element.parent());
            Color under = owner == null || !owner.bounds().contains(box) ? null : owner.view().fill();
            ElementRender cached = elementCache.get(element.key());
            if (cached != null && element.equals(elementSource.get(element.key())) && cached.bounds().equals(box)
                    && Objects.equals(under, elementUnder.get(element.key()))) {
                elements.put(element.key(), cached);
            } else {
                elements.put(element.key(), ElementPainter.render(definition.elementType(element.type()), element, box, measure, under));
            }
            elementUnder.put(element.key(), under);
        }
        elementCache.keySet().retainAll(diagram.elements().keySet());
        elementSource.keySet().retainAll(diagram.elements().keySet());
        elementUnder.keySet().retainAll(diagram.elements().keySet());
        elementCache.putAll(elements);
        diagram.elements().values().forEach(element -> elementSource.put(element.key(), element));

        router.obstacles(bounds);
        Set<Object> keys = new HashSet<>(before.elements().keySet());
        keys.addAll(elements.keySet());
        for (Object key : keys) {
            ElementRender was = before.elements().get(key);
            ElementRender now = elements.get(key);
            Rectangle2D from = was == null ? null : was.bounds();
            Rectangle2D to = now == null ? null : now.bounds();
            if (!Objects.equals(from, to)) {
                router.moved(from, to);
            }
        }
        router.retain(diagram.connections().keySet());

        Map<Object, ConnectionRender> connections = new LinkedHashMap<>();
        FontRenderContext frc = fontRenderContext();
        for (Connection connection : diagram.connections().values()) {
            ElementRender source = elements.get(connection.source().elementKey());
            ElementRender target = elements.get(connection.target().elementKey());
            if (source == null || target == null) {
                continue;
            }
            ConnectionRender render = connect(connection, source, target, before.connections().get(connection.key()), frc);
            connections.put(connection.key(), render);
        }
        connectionSource.keySet().retainAll(diagram.connections().keySet());
        diagram.connections().values().forEach(connection -> connectionSource.put(connection.key(), connection));

        Rectangle2D extent = null;
        for (ElementRender render : elements.values()) {
            extent = union(extent, render.extent());
        }
        for (ConnectionRender render : connections.values()) {
            extent = union(extent, render.extent());
        }
        canvas.setScene(new Scene(elements, connections, extent == null ? new Rectangle2D.Double() : extent, diagram.order()));
    }

    /** Where every shown element is: from the layout, or the file's position and the type's sizing. */
    private Map<Object, Rectangle2D> bounds(Diagram diagram) {
        Map<Object, Rectangle2D> bounds = new LinkedHashMap<>();
        DiagramLayout layout = definition.layout();
        if (layout != null) {
            Map<Object, Rectangle2D> laid = layout.layout(diagram, viewState(), this::size);
            for (Object key : diagram.elements().keySet()) {
                Rectangle2D box = laid.get(key);
                if (box != null) {
                    bounds.put(key, box);
                }
            }
            return bounds;
        }
        // simplified: file coordinates are not scaled with the IDE's user scale, while declared sizes and fonts are;
        // if positioned diagrams crowd at large scales, scale positions here and unscale them in DiagramCommands
        for (Element element : diagram.elements().values()) {
            Rectangle2D stored = element.bounds();
            Dimension2D size = size(element);
            bounds.put(element.key(), new Rectangle2D.Double(stored == null ? 0 : stored.getX(), stored == null ? 0 : stored.getY(), size.getWidth(),
                    size.getHeight()));
        }
        return bounds;
    }

    /** An element's drawn size, measured again only when the element changed. */
    private Dimension2D size(Element element) {
        Dimension2D cached = sizeCache.get(element.key());
        if (cached != null && element.equals(elementSource.get(element.key()))) {
            return cached;
        }
        Dimension2D size = measure.size(definition.elementType(element.type()), element);
        sizeCache.put(element.key(), size);
        return size;
    }

    private ConnectionRender connect(Connection connection, ElementRender source, ElementRender target, ConnectionRender before,
            FontRenderContext frc) {
        ConnectionType type = definition.connectionType(connection.type());
        List<Point2D> waypoints = connection.waypoints();
        Point2D sourceCentre = centre(source.bounds());
        Point2D targetCentre = centre(target.bounds());
        Point2D p = AnchorGeometry.attach(source.type(), source.bounds(), connection.source().anchorId(),
                waypoints.isEmpty() ? targetCentre : waypoints.get(0));
        Point2D q = AnchorGeometry.attach(target.type(), target.bounds(), connection.target().anchorId(),
                waypoints.isEmpty() ? sourceCentre : waypoints.get(waypoints.size() - 1));
        Anchor sourceAnchor = source.type() == null || connection.source().anchorId() == null ? null : source.type().anchor(connection.source().anchorId());
        Anchor targetAnchor = target.type() == null || connection.target().anchorId() == null ? null : target.type().anchor(connection.target().anchorId());
        LineStyle line = type == null ? LineStyle.STRAIGHT
                : connection.style() != null && connection.style().line() != null ? connection.style().line() : type.line();
        List<Point2D> route;
        if (!waypoints.isEmpty()) {
            // simplified: stored waypoints are drawn as given, never routed; route between them when a format needs it
            route = new ArrayList<>();
            route.add(p);
            route.addAll(waypoints);
            route.add(q);
        } else if (line == LineStyle.ORTHOGONAL && type.routed()) {
            route = router.route(connection.key(), p, q);
        } else if (line == LineStyle.ORTHOGONAL) {
            route = elbow(p, q, AnchorGeometry.exit(sourceAnchor));
        } else {
            route = List.of(p, q);
        }
        if (before != null && connection.equals(connectionSource.get(connection.key())) && before.route().equals(route)) {
            return before;
        }
        return ConnectionPainter.render(type, connection, route, AnchorGeometry.exit(sourceAnchor), AnchorGeometry.exit(targetAnchor), frc);
    }

    /** An orthogonal line that is not routed: straight when aligned, else a Z leaving the way the source anchor faces. */
    private static List<Point2D> elbow(Point2D p, Point2D q, Point2D exit) {
        if (p.getX() == q.getX() || p.getY() == q.getY()) {
            return List.of(p, q);
        }
        if (exit.getY() != 0) {
            double my = (p.getY() + q.getY()) / 2;
            return List.of(p, new Point2D.Double(p.getX(), my), new Point2D.Double(q.getX(), my), q);
        }
        double mx = (p.getX() + q.getX()) / 2;
        return List.of(p, new Point2D.Double(mx, p.getY()), new Point2D.Double(mx, q.getY()), q);
    }

    private static Point2D centre(Rectangle2D box) {
        return new Point2D.Double(box.getCenterX(), box.getCenterY());
    }

    private static Rectangle2D union(Rectangle2D a, Rectangle2D b) {
        if (a == null) {
            return (Rectangle2D) b.clone();
        }
        a.add(b);
        return a;
    }

    // DiagramCommands.Host

    @Override
    public DiagramDefinition definition() {
        return definition;
    }

    @Override
    public DiagramMapping mapping() {
        return mapping;
    }

    /** The diagram read from the document as it is now, or {@code null} while a problem is shown. */
    @Override
    public Diagram diagram() {
        Object model = model();
        return model == null ? null : diagramOf(model);
    }

    @Override
    public CharSequence text() {
        return document().getImmutableCharSequence();
    }

    /** Through the zoom and the scroll position. */
    @Override
    public Point2D toViewport(Point2D diagramPoint) {
        Point at = canvas.toCanvas(diagramPoint);
        Point origin = canvas.viewportPosition();
        return new Point2D.Double(at.x - origin.x, at.y - origin.y);
    }

    /** Every gesture of this designer: permissions, rules, then the mapping, as one command. */
    public DiagramCommands commands() {
        return commands;
    }

    /** The canvas the diagram is drawn on. */
    public DiagramCanvas canvas() {
        return canvas;
    }

    /** How an element is drawn, or {@code null} when it is not shown. */
    public ElementView elementView(Object key) {
        ElementRender render = model() == null ? null : canvas.scene().elements().get(key);
        return render == null ? null : render.view();
    }

    /** How a connection is drawn, or {@code null} when it is not shown. */
    public ConnectionView connectionView(Object key) {
        ConnectionRender render = model() == null ? null : canvas.scene().connections().get(key);
        return render == null ? null : render.view();
    }

    /** The shown elements' keys, in painting order. */
    public List<Object> elementKeys() {
        return model() == null ? List.of() : List.copyOf(canvas.scene().elements().keySet());
    }

    /** The shown connections' keys, in painting order. */
    public List<Object> connectionKeys() {
        return model() == null ? List.of() : List.copyOf(canvas.scene().connections().keySet());
    }

    /** A shown element's anchors, in declaration order; none for a placeholder. */
    public List<AnchorView> anchorsOf(Object key) {
        ElementRender render = model() == null ? null : canvas.scene().elements().get(key);
        return render == null ? List.of() : render.anchors();
    }

    /** The resize handles a selected element offers: those its type's resize allows. */
    public List<Handle> handlesOf(Object key) {
        Diagram diagram = diagram();
        Element element = diagram == null ? null : diagram.element(key);
        if (element == null || !selection().contains(key) || elementView(key) == null) {
            return List.of();
        }
        ElementType type = definition.elementType(element.type());
        return type == null || !type.selectable() || element.bounds() == null ? List.of() : Handle.allowed(type.resize());
    }

    /**
     * Where a text is drawn, in diagram coordinates, or {@code null} when it is not: an element's
     * text slot by id, or a connection's label by slot name ({@code MIDDLE}, {@code SOURCE},
     * {@code TARGET}) or by the property it shows.
     */
    public Rectangle2D textBounds(Object key, String slotOrLabel) {
        if (model() == null) {
            return null;
        }
        ElementRender element = canvas.scene().elements().get(key);
        if (element != null) {
            for (PlacedText text : element.texts()) {
                if (text.slot().id().equals(slotOrLabel)) {
                    return (Rectangle2D) text.box().clone();
                }
            }
            return null;
        }
        ConnectionRender connection = canvas.scene().connections().get(key);
        ConnectionType type = connection == null ? null : definition.connectionType(connection.view().type());
        if (type == null) {
            return null;
        }
        for (Map.Entry<LabelSlot, PlacedLabel> label : connection.labels().entrySet()) {
            if (label.getKey().name().equalsIgnoreCase(slotOrLabel) || type.labels().get(label.getKey()).property().equals(slotOrLabel)) {
                return (Rectangle2D) label.getValue().box().clone();
            }
        }
        return null;
    }

    /** What the definition's listener was told after the last re-read; empty after opening. */
    public List<DiagramChange> lastChanges() {
        return lastChanges;
    }

    /** The refusal of the last gesture, or {@code null} when it was allowed. */
    public Verdict lastRefusal() {
        return DiagramCommands.lastRefusal(this);
    }

    /** The connection type the next drag between anchors creates, chosen in the toolbox, or {@code null} for the first the anchor accepts. */
    public String armedConnectionType() {
        return armedConnectionType;
    }

    public void armConnectionType(String connectionType) {
        this.armedConnectionType = connectionType;
    }

    /** A designer-specific edit (FreeMind's fold) as one named command; {@code edit} gets the current text. */
    public void runCommand(String label, Function<CharSequence, TextChanges> edit, Runnable reselect) {
        if (model() == null) {
            return;
        }
        execute(label, edit.apply(text()), reselect);
    }

    /** A {@link NodeView} from the element's view, for the spec 002 test kit (research R19). */
    @Override
    public NodeView viewOf(Object key) {
        ElementView view = elementView(key);
        if (view == null) {
            return null;
        }
        String text = view.texts().values().stream().filter(t -> !t.isEmpty()).findFirst().orElse("");
        return new NodeView(key, view.bounds(), text, view.text(), view.plate() != null ? view.plate() : view.fill(), view.font(), List.of(), false,
                false, false);
    }

    /** Every selectable element and every connection, in document order, for Select All. */
    @Override
    protected List<?> allKeys() {
        Diagram diagram = diagram();
        if (diagram == null) {
            return List.of();
        }
        List<Object> keys = new ArrayList<>();
        for (Element element : diagram.elements().values()) {
            ElementType type = definition.elementType(element.type());
            if ((type == null || type.selectable()) && canvas.scene().elements().containsKey(element.key())) {
                keys.add(element.key());
            }
        }
        keys.addAll(canvas.scene().connections().keySet());
        return keys;
    }

    /** Select the element or connection and scroll it into view. */
    @Override
    public void reveal(Object key) {
        if (model() == null) {
            return;
        }
        ElementRender element = canvas.scene().elements().get(key);
        ConnectionRender connection = canvas.scene().connections().get(key);
        Rectangle2D area = element != null ? element.extent() : connection != null ? connection.extent() : null;
        if (area == null) {
            return;
        }
        select(List.of(key));
        Rectangle visible = canvas.toCanvas(area);
        int margin = JBUI.scale(24);
        visible.grow(margin, margin);
        canvas.scrollRectToVisible(visible);
    }

    /**
     * Move the selection from {@code from} (an element key, or {@code null} for none) one step
     * {@code heading}. By default: the nearest shown element whose centre lies that way, weighing
     * distance across twice as much as distance along.
     */
    protected void navigate(Object from, Heading heading) {
        Map<Object, ElementRender> elements = canvas.scene().elements();
        ElementRender start = from == null ? null : elements.get(from);
        if (start == null) {
            elements.keySet().stream().findFirst().ifPresent(this::reveal);
            return;
        }
        Point2D origin = centre(start.bounds());
        Object best = null;
        double bestScore = Double.MAX_VALUE;
        for (ElementRender render : elements.values()) {
            if (render == start) {
                continue;
            }
            Point2D c = centre(render.bounds());
            double dx = c.getX() - origin.getX();
            double dy = c.getY() - origin.getY();
            double along = switch (heading) {
            case UP -> -dy;
            case DOWN -> dy;
            case LEFT -> -dx;
            case RIGHT -> dx;
            };
            double across = heading == Heading.UP || heading == Heading.DOWN ? Math.abs(dx) : Math.abs(dy);
            if (along <= 0) {
                continue;
            }
            double score = along + 2 * across;
            if (score < bestScore) {
                bestScore = score;
                best = render.view().key();
            }
        }
        if (best != null) {
            reveal(best);
        }
    }

    @Override
    public @NotNull StructureViewBuilder getStructureViewBuilder() {
        return new DiagramStructureView(this);
    }

    /** Arrow keys move the selection through {@link #navigate}; keys with Ctrl, Alt or Meta are left to actions. */
    private final class ArrowKeys implements CanvasTool {

        @Override
        public void keyPressed(KeyEvent e) {
            if ((e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) != 0
                    || model() == null) {
                return;
            }
            Heading heading = switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> Heading.UP;
            case KeyEvent.VK_DOWN -> Heading.DOWN;
            case KeyEvent.VK_LEFT -> Heading.LEFT;
            case KeyEvent.VK_RIGHT -> Heading.RIGHT;
            default -> null;
            };
            if (heading == null) {
                return;
            }
            e.consume();
            List<Object> selection = selection();
            Object from = null;
            for (int i = selection.size() - 1; i >= 0 && from == null; i--) {
                if (canvas.scene().elements().containsKey(selection.get(i))) {
                    from = selection.get(i);
                }
            }
            navigate(from, heading);
        }
    }
}
