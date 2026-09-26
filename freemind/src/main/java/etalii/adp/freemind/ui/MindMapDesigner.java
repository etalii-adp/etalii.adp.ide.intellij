package etalii.adp.freemind.ui;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import org.jetbrains.annotations.NotNull;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.scale.JBUIScale;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.NodeView;
import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.TextSlot;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.properties.InPlaceEditor;
import etalii.adp.core.diagram.view.CanvasLayer;
import etalii.adp.core.diagram.view.CanvasTool;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;
import etalii.adp.core.diagram.view.ElementMeasure;
import etalii.adp.core.diagram.view.ElementView;
import etalii.adp.core.diagram.view.Scene.ElementRender;
import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.Side;
import etalii.adp.freemind.parse.MindMapParser;
import etalii.adp.freemind.ui.actions.MindMapAction;

/**
 * The FreeMind designer on the diagram framework (research R19): its model is the parsed
 * {@link MindMap}, shown as the diagram {@link FreeMindMapping} makes of it. The framework draws,
 * selects, drops, deletes and edits in place; what is FreeMind's own stays here: folding for
 * display only, tree navigation with the arrow keys, following links, renaming formatted text after
 * asking, and spec 001's names for its commands. Selection and display-only folding are kept by
 * {@link NodeKey}, so they survive every re-parse, including one that fails in between.
 */
public final class MindMapDesigner extends DiagramDesigner {

    /** The context menu group: FreeMind's actions, then zoom. It replaces the framework's on this canvas. */
    public static final String POPUP_GROUP = "etalii.adp.freemind.DesignerPopup";

    public static final String TITLE = "Rename Node";
    public static final String RICH_WARNING = "This node's text is formatted. Renaming it replaces the formatting with plain text.";

    private final FreeMindMapping freeMind;
    private MindMap shownMap;
    private Diagram shownDiagram;
    private float shownScale;
    private int folds;
    private int shownFolds;
    private Runnable alsoReselect;

    public MindMapDesigner(Project project, VirtualFile file, Document document) {
        this(project, file, document, new FreeMindMapping());
    }

    private MindMapDesigner(Project project, VirtualFile file, Document document, FreeMindMapping mapping) {
        super(project, file, document, FreeMindDefinition.DEFINITION, mapping);
        this.freeMind = mapping;
    }

    @Override
    protected MindMap parse(CharSequence text) throws FormatProblem {
        return MindMapParser.parse(text.toString());
    }

    /** The map as it is in the document now, or {@code null} while a problem is shown. */
    @Override
    public MindMap model() {
        return (MindMap) super.model();
    }

    /** Made again only for a new parse, a new display folding or a new IDE scale (for the file's fonts). */
    @Override
    protected Diagram diagramOf(Object model) {
        MindMap map = (MindMap) model;
        float scale = JBUIScale.scale(1f);
        if (map != shownMap || folds != shownFolds || scale != shownScale) {
            shownDiagram = freeMind.diagram(map, this::isShownFolded);
            shownMap = map;
            shownFolds = folds;
            shownScale = scale;
        }
        return shownDiagram;
    }

    @Override
    protected DiagramCanvas createCanvas() {
        return new MindMapCanvas(this);
    }

    @Override
    protected JComponent createView() {
        JComponent view = super.createView();
        canvas().addTool(new Keys());
        canvas().addTool(new Clicks());
        canvas().addLayer(new Underlines());
        return view;
    }

    /** The framework's context menu is FreeMind's here: its actions, then zoom. */
    @Override
    public void installActions(JComponent component, String groupId) {
        super.installActions(component, DiagramDesigner.POPUP_GROUP.equals(groupId) ? POPUP_GROUP : groupId);
    }

    /** The canvas the map is drawn on. */
    @Override
    public MindMapCanvas canvas() {
        return (MindMapCanvas) super.canvas();
    }

    /**
     * Every command, the framework's included: an edit built by the mapping keeps its spec 001 name,
     * a node moved into a folded branch shows it open for display, and a reselect asked for with
     * {@link #withReselect} runs inside the command.
     */
    @Override
    public void execute(String label, TextChanges changes, Runnable andThen) {
        String named = freeMind.labelOf(changes);
        NodeKey unfold = freeMind.unfoldFor(changes);
        Runnable also = alsoReselect;
        MindMap map = model();
        MapNode target = map == null || unfold == null ? null : map.node(unfold);
        if (target != null && isShownFolded(target)) {
            setShownFolded(target, false);
        }
        super.execute(named != null ? named : label, changes, also == null ? andThen : () -> {
            andThen.run();
            also.run();
        });
    }

    /** Run a gesture of the framework's commands; a command it runs also runs {@code reselect}, inside it. */
    public void withReselect(Runnable reselect, Runnable gesture) {
        alsoReselect = reselect;
        try {
            gesture.run();
        } finally {
            alsoReselect = null;
        }
    }

    /** Whether a node is drawn collapsed: its {@code FOLDED}, unless folded or unfolded for display only. A leaf never is. */
    public boolean isShownFolded(MapNode node) {
        if (node.children().isEmpty()) {
            return false;
        }
        Boolean override = viewState().foldOverride(node.key());
        return override != null ? override : node.folded();
    }

    /** Collapse or expand for display only, without an edit (read-only files, reveal). */
    public void setShownFolded(MapNode node, boolean folded) {
        viewState().setFoldOverride(node.key(), folded == node.folded() ? null : folded);
        foldsChanged();
    }

    /** The display folding changed: the diagram is made again with its fold markers, and laid out. */
    private void foldsChanged() {
        folds++;
        MindMap map = model();
        if (map != null) {
            modelChanged(map);
        }
    }

    /** Expand the node's folded ancestors for display only, select it and scroll it into view. */
    @Override
    public void reveal(Object key) {
        MindMap map = model();
        MapNode node = map == null || !(key instanceof NodeKey nodeKey) ? null : map.node(nodeKey);
        if (node == null) {
            return;
        }
        boolean changed = false;
        for (MapNode ancestor = node.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (isShownFolded(ancestor)) {
                viewState().setFoldOverride(ancestor.key(), ancestor.folded() ? Boolean.FALSE : null);
                changed = true;
            }
        }
        if (changed) {
            foldsChanged();
        }
        select(List.of(node.key()));
        canvas().scrollTo(node.key());
    }

    /**
     * Rename in place (spec 001 FR-020): the framework's in-place editor on the node's text, first
     * bringing it into view when a folded branch hides it. A plain node's new text is set through
     * the commands; a formatted node's replaces its formatting only after the user confirms it.
     */
    public void rename(NodeKey key) {
        MindMap map = model();
        MapNode node = map == null ? null : map.node(key);
        if (node == null || !isEditable()) {
            return;
        }
        if (elementView(key) == null) {
            reveal(key);
        } else {
            canvas().scrollTo(key);
        }
        Rectangle2D box = textBounds(key, FreeMindMapping.TEXT);
        Element element = diagram().element(key);
        if (box != null && element != null) {
            InPlaceEditor.open(this, new InPlaceEditor.Target(key, FreeMindMapping.TEXT, FreeMindMapping.TEXT, box, false, false,
                    ElementMeasure.font(TextSlot.Style.PLAIN, element)), text -> renamed(key, text));
        }
    }

    private void renamed(NodeKey key, String text) {
        MindMap map = model();
        MapNode node = map == null ? null : map.node(key);
        if (node == null || !isEditable()) {
            return;
        }
        if (!node.rich()) {
            commands().setProperty(List.of(key), FreeMindMapping.TEXT, text);
            return;
        }
        if (Messages.showOkCancelDialog(project(), RICH_WARNING, TITLE, "Rename", Messages.getCancelButton(), Messages.getWarningIcon()) == Messages.OK) {
            runCommand(MindMapEdits.RENAME, current -> MindMapEdits.rename(map, key, text, MindMapAction.now()).changes(), () -> select(List.of(key)));
        }
    }

    /** As the framework draws it, with the icons, indicators and fold state FreeMind shows (research R19). */
    @Override
    public NodeView viewOf(Object key) {
        ElementView view = elementView(key);
        MindMap map = model();
        MapNode node = view == null || !(key instanceof NodeKey nodeKey) ? null : map.node(nodeKey);
        Element element = node == null ? null : diagram().element(key);
        if (element == null) {
            return null;
        }
        String indicators = view.texts().getOrDefault(FreeMindMapping.INDICATORS, "");
        boolean ownBackground = element.style() != null && element.style().fill() != null;
        return new NodeView(key, view.bounds(), element.property(FreeMindMapping.TEXT), view.text(), ownBackground ? view.fill() : view.plate(),
                view.font(), node.icons().stream().map(FreeMindIcons::display).toList(), indicators.contains(NodePainter.LINK_GLYPH),
                indicators.contains(NodePainter.NOTE_GLYPH), !view.texts().getOrDefault(FreeMindMapping.FOLD_MARKER, "").isEmpty());
    }

    /** The drawn arrow links, in file order. */
    public List<MindMapLayout.Arrow> arrows() {
        MindMap map = model();
        List<MindMapLayout.Arrow> arrows = new ArrayList<>();
        if (map == null) {
            return arrows;
        }
        Map<NodeKey, Integer> counts = new HashMap<>();
        for (ArrowLink link : map.arrowLinks()) {
            int index = counts.merge(link.source(), 1, Integer::sum) - 1;
            MapNode destination = map.nodeById(link.destinationId());
            if (destination != null && connectionView(new FreeMindMapping.ArrowKey(link.source(), index)) != null) {
                arrows.add(new MindMapLayout.Arrow(link, link.source(), destination.key(), arrowhead(link.startArrow(), false),
                        arrowhead(link.endArrow(), true)));
            }
        }
        return arrows;
    }

    /** How an arrow link is drawn, or {@code null} when it is not (missing destination, or an end folded away). */
    public MindMapLayout.Arrow arrowOf(ArrowLink link) {
        return arrows().stream().filter(arrow -> arrow.link().equals(link)).findFirst().orElse(null);
    }

    private static boolean arrowhead(String value, boolean byDefault) {
        return value == null ? byDefault : !"None".equals(value);
    }

    /** Every node, in document order, for Select All; branches and arrow links are not selected with them. */
    @Override
    protected List<?> allKeys() {
        MindMap map = model();
        List<NodeKey> keys = new ArrayList<>();
        if (map != null) {
            collect(map.root(), keys);
        }
        return keys;
    }

    private static void collect(MapNode node, List<NodeKey> keys) {
        keys.add(node.key());
        for (MapNode child : node.children()) {
            collect(child, keys);
        }
    }

    /**
     * Arrow keys move the selection as in FreeMind: up and down between siblings on the same side,
     * left and right towards or away from the root; from nothing, to the root.
     */
    @Override
    protected void navigate(Object from, Heading heading) {
        step(from, heading, false);
    }

    private void step(Object from, Heading heading, boolean extend) {
        MindMap map = model();
        if (map == null) {
            return;
        }
        MapNode current = from instanceof NodeKey key ? map.node(key) : null;
        MapNode next;
        if (current == null || heading == null) {
            next = map.root();
        } else {
            next = switch (heading) {
            case UP -> sibling(map, current, -1);
            case DOWN -> sibling(map, current, 1);
            case LEFT -> sideways(map, current, Side.LEFT);
            case RIGHT -> sideways(map, current, Side.RIGHT);
            };
        }
        if (next != null) {
            List<Object> selection = new ArrayList<>(extend ? selection() : List.of());
            selection.remove(next.key());
            selection.add(next.key());
            select(selection);
            canvas().scrollTo(next.key());
        }
    }

    /** The drawn sibling above or below, on the same side of the root. */
    private MapNode sibling(MindMap map, MapNode node, int direction) {
        MapNode parent = node.parent();
        if (parent == null) {
            return null;
        }
        Side side = map.sideOf(node);
        List<MapNode> siblings = parent.children().stream().filter(n -> map.sideOf(n) == side && elementView(n.key()) != null).toList();
        int index = siblings.indexOf(node) + direction;
        return index >= 0 && index < siblings.size() ? siblings.get(index) : null;
    }

    /** Towards the root when the node is on {@code towards}'s other side, else out to its middle child. */
    private MapNode sideways(MindMap map, MapNode node, Side towards) {
        MapNode branch = node;
        while (branch.parent() != null && branch.parent().parent() != null) {
            branch = branch.parent();
        }
        Side side = branch.parent() == null ? null : map.sideOf(branch);
        if (side != null && side != towards) {
            return node.parent();
        }
        if (isShownFolded(node)) {
            return null;
        }
        List<MapNode> children = node.children().stream()
                .filter(n -> (node.parent() != null || map.sideOf(n) == towards) && elementView(n.key()) != null).toList();
        double centre = elementView(node.key()).bounds().getCenterY();
        return children.stream().min(Comparator.comparingDouble(n -> Math.abs(elementView(n.key()).bounds().getCenterY() - centre))).orElse(null);
    }

    @Override
    public @NotNull StructureViewBuilder getStructureViewBuilder() {
        return new MindMapStructureView(this);
    }

    @Override
    public @NotNull String getName() {
        return "Mind Map";
    }

    /** Home selects the root, and Shift with an arrow key adds the node it moves to; plain arrow keys are the framework's. */
    private final class Keys implements CanvasTool {

        @Override
        public void keyPressed(KeyEvent e) {
            int modifiers = e.getModifiersEx();
            if ((modifiers & (InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK)) != 0 || model() == null) {
                return;
            }
            Heading heading = switch (e.getKeyCode()) {
            case KeyEvent.VK_UP -> Heading.UP;
            case KeyEvent.VK_DOWN -> Heading.DOWN;
            case KeyEvent.VK_LEFT -> Heading.LEFT;
            case KeyEvent.VK_RIGHT -> Heading.RIGHT;
            default -> null;
            };
            if (e.getKeyCode() == KeyEvent.VK_HOME) {
                e.consume();
                step(null, null, (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0);
            } else if (heading != null && (modifiers & InputEvent.SHIFT_DOWN_MASK) != 0) {
                e.consume();
                List<Object> selection = selection();
                step(selection.isEmpty() ? null : selection.get(selection.size() - 1), heading, true);
            }
        }
    }

    /** A click on a link indicator follows the link; a double-click on a node the framework does not edit, a formatted one, renames it. */
    private final class Clicks implements CanvasTool {

        @Override
        public void mouseClicked(MouseEvent e) {
            if (!SwingUtilities.isLeftMouseButton(e)) {
                return;
            }
            NodeKey key = canvas().keyAt(e.getPoint());
            if (key == null) {
                return;
            }
            if (e.getClickCount() == 1 && contains(canvas().indicatorBounds(key, NodePainter.Indicator.LINK), e)) {
                e.consume();
                LinkOpener.open(MindMapDesigner.this, canvas().linkOf(key));
            } else if (e.getClickCount() == 2 && isEditable()) {
                e.consume();
                rename(key);
            }
        }

        private static boolean contains(java.awt.Rectangle box, MouseEvent e) {
            return box != null && box.contains(e.getPoint());
        }
    }

    /** A fork is drawn as FreeMind draws it: its text on a line (research R19). */
    private final class Underlines implements CanvasLayer {

        @Override
        public void paint(Graphics2D g, DiagramCanvas canvas, Rectangle2D visible) {
            g.setColor(NodePainter.CONNECTOR);
            g.setStroke(new BasicStroke(JBUIScale.scale(1f)));
            for (ElementRender render : canvas.scene().elements().values()) {
                Rectangle2D box = render.bounds();
                if (FreeMindDefinition.NODE.equals(render.view().type()) && box.intersects(visible)) {
                    g.draw(new Line2D.Double(box.getX(), box.getMaxY() - 0.5, box.getMaxX(), box.getMaxY() - 0.5));
                }
            }
        }
    }
}
