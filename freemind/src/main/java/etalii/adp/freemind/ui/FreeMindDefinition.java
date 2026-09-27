package etalii.adp.freemind.ui;

import static etalii.adp.freemind.ui.FreeMindMapping.BACKGROUND_COLOR;
import static etalii.adp.freemind.ui.FreeMindMapping.COLOR;
import static etalii.adp.freemind.ui.FreeMindMapping.FOLDED;
import static etalii.adp.freemind.ui.FreeMindMapping.FOLD_MARKER;
import static etalii.adp.freemind.ui.FreeMindMapping.ICONS;
import static etalii.adp.freemind.ui.FreeMindMapping.ID;
import static etalii.adp.freemind.ui.FreeMindMapping.INDICATORS;
import static etalii.adp.freemind.ui.FreeMindMapping.LINK;
import static etalii.adp.freemind.ui.FreeMindMapping.RICH;
import static etalii.adp.freemind.ui.FreeMindMapping.TEXT;

import java.util.Set;

import etalii.adp.core.diagram.ArrowHead;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.DiagramRules;
import etalii.adp.core.diagram.Direction;
import etalii.adp.core.diagram.EditorKind;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.LineStyle;
import etalii.adp.core.diagram.Outline;
import etalii.adp.core.diagram.Placement;
import etalii.adp.core.diagram.Sizing;
import etalii.adp.core.diagram.SlotPosition;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.model.End;
import etalii.adp.core.settings.CanvasOption;
import etalii.adp.freemind.ui.FreeMindMapping.BranchKey;
import etalii.adp.freemind.ui.actions.MindMapAction;

/**
 * FreeMind maps as a diagram (research R19): the root, nodes drawn as forks or bubbles by their
 * {@code STYLE}, the branches the tree draws and the arrow links the user draws. Nodes are laid
 * out by {@link MindMapLayout}, never placed, so a node moves by being dropped onto another; the
 * grid is neither shown nor snapped to, whatever the user chose (spec 004, FR-012).
 */
public final class FreeMindDefinition {

    public static final String ROOT = "root";
    public static final String NODE = "node";
    public static final String BUBBLE = "bubble";
    public static final String BRANCH = "branch";
    public static final String ARROW_LINK = "arrowLink";

    /** Where branches and arrow links attach: the middle of a node's left and right sides. */
    public static final String LEFT = "left";
    public static final String RIGHT = "right";

    public static final String NEEDS_PARENT = "a node needs a parent";
    public static final String BRANCH_GOES_WITH_NODE = "remove the node to remove its branch";
    static final String FORMATTED = "The text is formatted; rename the node to replace it with plain text";
    static final String OWN_BRANCH = "A node cannot move into its own branch";
    static final String ONE_AT_A_TIME = "Drag one node at a time";
    static final String NO_ID = "The node has no ID for an arrow link to point to";

    /** A node is as wide as its longest line (spec 001), so the width is practically not capped. */
    private static final int MAX_WIDTH = 10_000;

    public static final DiagramDefinition DEFINITION = builder().build();

    private FreeMindDefinition() {
    }

    public static DiagramDefinition.Builder builder() {
        return DiagramDefinition.builder("freemind")
                .element(ROOT, e -> node(e).label("Root").outline(Outline.ELLIPSE).tone(NodePainter.ROOT).droppableOnto(false))
                .element(NODE, e -> node(e).label("Node").outline(Outline.RECTANGLE).tone(NodePainter.FORK))
                .element(BUBBLE, e -> node(e).label("Bubble").outline(Outline.ROUNDED_RECTANGLE).tone(NodePainter.BUBBLE))
                .connection(BRANCH, c -> c.label("Branch").line(LineStyle.CURVED).tone(NodePainter.BRANCH).userConnectable(false))
                .connection(ARROW_LINK, c -> c.label("Arrow Link").line(LineStyle.CURVED).thickness(1.2f).tone(NodePainter.LINK)
                        .arrows(ArrowHead.NONE, ArrowHead.FILLED))
                .toolbox(NODE, ARROW_LINK)
                .rules(new Rules())
                .view(v -> v.fix(CanvasOption.SHOW_GRID, false).fix(CanvasOption.SNAP_TO_GRID, false))
                .layout(new MindMapLayout());
    }

    /**
     * What every node type shares. The text is edited in a single-line field, as spec 001's rename
     * is; the icons, indicators and fold marker are shown and never edited.
     */
    // simplified: text is single-line in place and in the panel, as spec 001's rename was; make it MULTILINE when multi-line text must be typed
    private static ElementType.Builder node(ElementType.Builder e) {
        return e.sizing(Sizing.auto(MAX_WIDTH)).movable(false).droppableOnto(true)
                .text(TEXT, t -> t.editable(true))
                .text(ICONS, t -> t.position(SlotPosition.LEFT))
                .text(INDICATORS, t -> t.position(SlotPosition.RIGHT))
                .text(FOLD_MARKER, t -> t.position(SlotPosition.RIGHT))
                .anchor(LEFT, a -> a.at(0, 0.5).visible(false).accepts(ARROW_LINK, Direction.BOTH))
                .anchor(RIGHT, a -> a.at(1, 0.5).visible(false).accepts(ARROW_LINK, Direction.BOTH))
                .property(TEXT, p -> p.label("Text").defaultValue(MindMapAction.NEW_NODE_TEXT))
                .property(ID, p -> p.label("ID").readOnly(true))
                .property(FOLDED, p -> p.label("Folded").editor(EditorKind.BOOLEAN))
                .property(LINK, p -> p.label("Link"))
                .property(COLOR, p -> p.label("Colour").editor(EditorKind.COLOR))
                .property(BACKGROUND_COLOR, p -> p.label("Background Colour").editor(EditorKind.COLOR))
                .property(ICONS, p -> p.label("Icons").category("Shown").readOnly(true))
                .property(INDICATORS, p -> p.label("Indicators").category("Shown").readOnly(true))
                .property(FOLD_MARKER, p -> p.label("Fold Marker").category("Shown").readOnly(true));
    }

    /** The tree's own rules: a node has one parent, the root stays, and a branch goes only with its node. */
    private static final class Rules implements DiagramRules {

        @Override
        public Verdict canAdd(Diagram d, String type, Object targetOrSector) {
            return d.element(targetOrSector) == null ? Verdict.refuse(NEEDS_PARENT) : Verdict.allow();
        }

        @Override
        public Verdict canRemove(Diagram d, Set<Object> keys) {
            for (Object key : keys) {
                Element element = d.element(key);
                if (element != null && element.parent() == null) {
                    return Verdict.refuse(MindMapAction.ROOT_CANNOT_BE_DELETED);
                }
            }
            return Verdict.allow();
        }

        @Override
        public Verdict canConnect(Diagram d, String type, End source, End target) {
            Element destination = d.element(target.elementKey());
            return destination == null || destination.property(ID).isEmpty() ? Verdict.refuse(NO_ID) : Verdict.allow();
        }

        @Override
        public Verdict canDisconnect(Diagram d, Object connection) {
            return connection instanceof BranchKey ? Verdict.refuse(BRANCH_GOES_WITH_NODE) : Verdict.allow();
        }

        @Override
        public Verdict canDrop(Diagram d, Set<Object> keys, Object target, Placement placement) {
            // simplified: one node is dragged at a time, as in spec 001; move several in one edit if it binds
            if (keys.size() != 1) {
                return Verdict.refuse(ONE_AT_A_TIME);
            }
            Object key = keys.iterator().next();
            Element element = d.element(key);
            Element onto = d.element(target);
            if (element.parent() == null) {
                return Verdict.refuse(MindMapAction.ROOT_CANNOT_BE_MOVED);
            }
            for (Element e = onto; e != null; e = e.parent() == null ? null : d.element(e.parent())) {
                if (e.key().equals(key)) {
                    return Verdict.refuse(OWN_BRANCH);
                }
            }
            return placement != Placement.INTO && onto.parent() == null ? Verdict.refuse(MindMapAction.ROOT_HAS_NO_SIBLINGS) : Verdict.allow();
        }

        @Override
        public Verdict canSetProperty(Diagram d, Object key, String property) {
            Element element = d.element(key);
            return TEXT.equals(property) && element != null && !element.property(RICH).isEmpty() ? Verdict.refuse(FORMATTED) : Verdict.allow();
        }
    }
}
