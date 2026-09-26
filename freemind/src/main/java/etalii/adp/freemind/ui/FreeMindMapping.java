package etalii.adp.freemind.ui;

import java.awt.Color;
import java.awt.Font;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.LongSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.Rgb;
import etalii.adp.core.TextChange;
import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.AddRequest;
import etalii.adp.core.diagram.ArrowHead;
import etalii.adp.core.diagram.BoundsChange;
import etalii.adp.core.diagram.DiagramMapping;
import etalii.adp.core.diagram.EndSide;
import etalii.adp.core.diagram.Placement;
import etalii.adp.core.diagram.model.Connection;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Element;
import etalii.adp.core.diagram.model.End;
import etalii.adp.core.diagram.model.StyleOverride;
import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.AttributeRange;
import etalii.adp.freemind.model.FontSpec;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.Side;
import etalii.adp.freemind.parse.MindMapParser;
import etalii.adp.freemind.ui.actions.MindMapAction;

/**
 * Between FreeMind maps and the diagram (research R5, R19), over {@link MindMapParser} and
 * {@link MindMapEdits}: every node is an element keyed by its {@link NodeKey}, every parent-child
 * line a {@code branch} and every arrow link whose destination exists an {@code arrowLink}. Edits
 * are the catalogue's, so they change what spec 001's edits change and nothing else.
 * <p>
 * The edits FreeMind names itself (a rename, a move, a delete) keep their spec 001 names:
 * {@link #labelOf} gives the name of the last edit built here, which the designer uses for the
 * command.
 */
public final class FreeMindMapping implements DiagramMapping {

    /** A branch, by the node it leads to. */
    public record BranchKey(NodeKey child) {
    }

    /** An arrow link, by its source and its place among the source's arrow links. */
    public record ArrowKey(NodeKey source, int index) {
    }

    public static final String TEXT = "text";
    public static final String ID = "id";
    public static final String FOLDED = "folded";
    public static final String LINK = "link";
    public static final String COLOR = "color";
    public static final String BACKGROUND_COLOR = "backgroundColor";
    public static final String ICONS = "icons";
    public static final String INDICATORS = "indicators";
    public static final String FOLD_MARKER = "foldMarker";

    /** Layout inputs and the formatted-text flag: element values the panel does not show. */
    static final String SIDE = "side";
    static final String HGAP = "hgap";
    static final String VGAP = "vgap";
    static final String VSHIFT = "vshift";
    static final String RICH = "rich";

    /** One edit built here: its spec 001 name, and a folded node to show open for display. */
    private record Made(TextChanges changes, String label, NodeKey unfold) {
    }

    private final LongSupplier clock;
    private final Supplier<RandomGenerator> random;
    private MindMap lastMap;
    private Diagram lastDiagram;
    private Made made;

    public FreeMindMapping() {
        this(MindMapAction::now, MindMapAction::random);
    }

    /** With a fixed clock and random source for {@code CREATED}, {@code MODIFIED} and new IDs. */
    FreeMindMapping(LongSupplier clock, Supplier<RandomGenerator> random) {
        this.clock = clock;
        this.random = random;
    }

    @Override
    public Diagram read(CharSequence text) throws FormatProblem {
        return diagram(MindMapParser.parse(text.toString()), null);
    }

    /**
     * The map as a diagram. {@code shownFolded} says which branches are drawn collapsed, for the
     * fold marker; {@code null} takes the file's {@code FOLDED}.
     */
    public Diagram diagram(MindMap map, Predicate<MapNode> shownFolded) {
        List<Element> elements = new ArrayList<>();
        List<Connection> connections = new ArrayList<>();
        add(map, map.root(), null, "fork", shownFolded, elements, connections);
        Map<NodeKey, Integer> counts = new HashMap<>();
        for (ArrowLink link : map.arrowLinks()) {
            int index = counts.merge(link.source(), 1, Integer::sum) - 1;
            MapNode destination = map.nodeById(link.destinationId());
            if (destination != null) {
                StyleOverride arrows = new StyleOverride(null, null, null, null, null, arrowhead(link.startArrow(), false),
                        arrowhead(link.endArrow(), true), null, null, false);
                connections.add(new Connection(new ArrowKey(link.source(), index), FreeMindDefinition.ARROW_LINK, new End(link.source(), null),
                        new End(destination.key(), null), Map.of(), arrows, List.of()));
            }
        }
        Diagram diagram = Diagram.of(elements, connections, List.of());
        lastMap = map;
        lastDiagram = diagram;
        return diagram;
    }

    private void add(MindMap map, MapNode node, Side side, String parentStyle, Predicate<MapNode> shownFolded, List<Element> elements,
            List<Connection> connections) {
        Side branchSide = node.parent() == map.root() ? map.sideOf(node) : side;
        AttributeRange styleAttribute = node.ranges().attribute("STYLE");
        String style = styleAttribute == null ? parentStyle : styleAttribute.value().of(map.text());
        style = style.equals("as_parent") ? parentStyle : style;
        boolean folded = !node.children().isEmpty() && (shownFolded == null ? node.folded() : shownFolded.test(node));
        boolean bubble = style.equals("bubble") || style.equals("combined") && folded;
        String type = node.parent() == null ? FreeMindDefinition.ROOT : bubble ? FreeMindDefinition.BUBBLE : FreeMindDefinition.NODE;

        Map<String, String> properties = new LinkedHashMap<>();
        properties.put(TEXT, node.text() == null ? "" : node.text());
        properties.put(ID, node.id() == null ? "" : node.id());
        properties.put(FOLDED, Boolean.toString(node.folded()));
        properties.put(LINK, node.link() == null ? "" : node.link());
        properties.put(COLOR, colour(node.color()));
        properties.put(BACKGROUND_COLOR, colour(node.backgroundColor()));
        // simplified: an icon without a glyph shows its name as plain text, without spec 001's badge border; draw badges in a layer if it binds
        properties.put(ICONS, node.icons().stream().map(FreeMindIcons::display).collect(Collectors.joining(" ")));
        List<String> indicators = new ArrayList<>();
        if (node.link() != null) {
            indicators.add(NodePainter.LINK_GLYPH);
        }
        if (node.note() != null) {
            indicators.add(NodePainter.NOTE_GLYPH);
        }
        properties.put(INDICATORS, String.join(" ", indicators));
        properties.put(FOLD_MARKER, folded ? NodePainter.FOLD_GLYPH : "");
        if (node.rich()) {
            properties.put(RICH, "true");
        }
        if (branchSide != null && node.parent() == map.root()) {
            properties.put(SIDE, branchSide == Side.LEFT ? "left" : "right");
        }
        putNumber(properties, HGAP, node.hgap());
        putNumber(properties, VGAP, node.vgap());
        putNumber(properties, VSHIFT, node.vshift());

        Color fill = color(node.backgroundColor());
        Color text = color(node.color());
        Font font = font(node.font());
        StyleOverride looks = fill == null && text == null && font == null ? null : new StyleOverride(fill, null, text, null, null, null, null, null, font, false);
        NodeKey parent = node.parent() == null ? null : node.parent().key();
        elements.add(new Element(node.key(), type, null, properties, null, parent, looks));
        if (parent != null) {
            boolean left = branchSide == Side.LEFT;
            connections.add(new Connection(new BranchKey(node.key()), FreeMindDefinition.BRANCH,
                    new End(parent, left ? FreeMindDefinition.LEFT : FreeMindDefinition.RIGHT),
                    new End(node.key(), left ? FreeMindDefinition.RIGHT : FreeMindDefinition.LEFT), Map.of(), null, List.of()));
        }
        for (MapNode child : node.children()) {
            add(map, child, branchSide, style, shownFolded, elements, connections);
        }
    }

    private static void putNumber(Map<String, String> properties, String property, int value) {
        if (value != 0) {
            properties.put(property, Integer.toString(value));
        }
    }

    /** FreeMind writes {@code None} for no arrowhead; any other value is one, and an absent one is the default. */
    private static ArrowHead arrowhead(String value, boolean byDefault) {
        return (value == null ? byDefault : !"None".equals(value)) ? ArrowHead.FILLED : ArrowHead.NONE;
    }

    private static String colour(Rgb rgb) {
        return rgb == null ? "" : String.format("#%02x%02x%02x", rgb.red(), rgb.green(), rgb.blue());
    }

    private static Color color(Rgb rgb) {
        return rgb == null ? null : new Color(rgb.red(), rgb.green(), rgb.blue());
    }

    /** The node's own {@code font} element, sizes in points scaled for the IDE, else {@code null}: the IDE's label font. */
    // simplified: the size is scaled when the map is read, so a change of the IDE scale shows at the next re-read; re-read on a scale change if it binds
    static Font font(FontSpec spec) {
        if (spec == null) {
            return null;
        }
        Font base = JBFont.label().asPlain();
        int style = (spec.bold() ? Font.BOLD : Font.PLAIN) | (spec.italic() ? Font.ITALIC : Font.PLAIN);
        String name = spec.name() != null ? spec.name() : base.getFamily();
        float size = spec.size() != null ? JBUIScale.scale((float) spec.size()) : base.getSize2D();
        return new Font(name, style, 12).deriveFont(size);
    }

    /** The arrow link's key: its source and how many of the source's links come before it. */
    public static ArrowKey keyOf(MindMap map, ArrowLink link) {
        int index = 0;
        for (ArrowLink other : map.arrowLinks()) {
            if (other == link) {
                break;
            }
            if (other.source().equals(link.source())) {
                index++;
            }
        }
        return new ArrowKey(link.source(), index);
    }

    /** The arrow link with this key, or {@code null}. */
    static ArrowLink linkOf(MindMap map, ArrowKey key) {
        int index = 0;
        for (ArrowLink link : map.arrowLinks()) {
            if (link.source().equals(key.source()) && index++ == key.index()) {
                return link;
            }
        }
        return null;
    }

    /** The spec 001 name of the last edit built here when these are its changes, else {@code null}. */
    String labelOf(TextChanges changes) {
        return made != null && made.changes() == changes ? made.label() : null;
    }

    /** The folded node the last edit built here puts a node into, when these are its changes, else {@code null}. */
    NodeKey unfoldFor(TextChanges changes) {
        return made != null && made.changes() == changes ? made.unfold() : null;
    }

    /** The map {@code diagram} was read from, re-read from {@code text} when it is not the last one read here. */
    private MindMap map(CharSequence text, Diagram diagram) {
        if (diagram == lastDiagram && lastMap != null) {
            return lastMap;
        }
        try {
            return MindMapParser.parse(text.toString());
        } catch (FormatProblem e) {
            return null;
        }
    }

    private TextChanges made(Edit edit, String label, NodeKey unfold) {
        if (edit == null) {
            return TextChanges.of();
        }
        made = new Made(edit.changes(), label, unfold);
        return edit.changes();
    }

    @Override
    public TextChanges add(CharSequence text, Diagram diagram, AddRequest request) {
        MindMap map = map(text, diagram);
        if (map == null || !(request.target() instanceof NodeKey target) || map.node(target) == null) {
            return TextChanges.of();
        }
        String label = request.properties().getOrDefault(TEXT, MindMapAction.NEW_NODE_TEXT);
        Edit edit = MindMapEdits.addChild(map, target, label, clock.getAsLong(), random.get());
        return made(edit, edit.label(), target);
    }

    @Override
    public TextChanges remove(CharSequence text, Diagram diagram, Set<Object> keys) {
        MindMap map = map(text, diagram);
        if (map == null) {
            return TextChanges.of();
        }
        List<MapNode> nodes = new ArrayList<>();
        List<ArrowLink> links = new ArrayList<>();
        for (Object key : keys) {
            MapNode node = key instanceof NodeKey nodeKey ? map.node(nodeKey) : null;
            ArrowLink link = key instanceof ArrowKey arrowKey ? linkOf(map, arrowKey) : null;
            if (node != null && node != map.root()) {
                nodes.add(node);
            } else if (link != null) {
                links.add(link);
            }
        }
        List<TextChange> changes = new ArrayList<>();
        Edit delete = nodes.isEmpty() ? null : MindMapEdits.delete(map, nodes.stream().map(MapNode::key).toList());
        if (delete != null) {
            changes.addAll(delete.changes().changes());
        }
        for (ArrowLink link : links) {
            MapNode destination = map.nodeById(link.destinationId());
            boolean goes = nodes.stream().anyMatch(n -> n.contains(map.node(link.source())) || destination != null && n.contains(destination));
            if (!goes) {
                changes.addAll(MindMapEdits.removeArrowLink(map, link).changes().changes());
            }
        }
        Edit edit = new Edit(delete != null ? delete.label() : MindMapEdits.REMOVE_ARROW_LINK, new TextChanges(changes), null);
        return made(edit, delete != null ? delete.label() : null, null);
    }

    /** Nodes are laid out, never placed: there is nothing to write. */
    @Override
    public TextChanges setBounds(CharSequence text, Diagram diagram, List<BoundsChange> changes) {
        return TextChanges.of();
    }

    @Override
    public TextChanges connect(CharSequence text, Diagram diagram, String connectionType, End source, End target) {
        MindMap map = map(text, diagram);
        if (map == null || !FreeMindDefinition.ARROW_LINK.equals(connectionType) || !(source.elementKey() instanceof NodeKey from)
                || !(target.elementKey() instanceof NodeKey to) || map.node(from) == null || map.node(to) == null) {
            return TextChanges.of();
        }
        return made(MindMapEdits.addArrowLink(map, from, to, random.get()), null, null);
    }

    // simplified: an arrow link is not reconnected, it is removed and drawn again; write DESTINATION or move the element when it binds
    @Override
    public TextChanges reconnect(CharSequence text, Diagram diagram, Object connection, EndSide side, End end) {
        return TextChanges.of();
    }

    @Override
    public TextChanges setProperty(CharSequence text, Diagram diagram, Set<Object> keys, String property, String value) {
        MindMap map = map(text, diagram);
        if (map == null) {
            return TextChanges.of();
        }
        long now = clock.getAsLong();
        boolean fold = "true".equals(value);
        List<TextChange> changes = new ArrayList<>();
        for (Object key : keys) {
            if (!(key instanceof NodeKey nodeKey) || map.node(nodeKey) == null) {
                continue;
            }
            Edit edit = switch (property) {
            case TEXT -> MindMapEdits.rename(map, nodeKey, value, now);
            case FOLDED -> MindMapEdits.setFolded(map, nodeKey, fold);
            case LINK -> MindMapEdits.setAttribute(map, nodeKey, "LINK", value);
            case COLOR -> MindMapEdits.setAttribute(map, nodeKey, "COLOR", value.toLowerCase(Locale.ROOT));
            case BACKGROUND_COLOR -> MindMapEdits.setAttribute(map, nodeKey, "BACKGROUND_COLOR", value.toLowerCase(Locale.ROOT));
            default -> null;
            };
            if (edit != null) {
                changes.addAll(edit.changes().changes());
            }
        }
        String label = switch (property) {
        case TEXT -> MindMapEdits.RENAME;
        case FOLDED -> fold ? MindMapEdits.FOLD : MindMapEdits.UNFOLD;
        default -> null;
        };
        return changes.isEmpty() ? TextChanges.of() : made(new Edit(label, new TextChanges(changes), null), label, null);
    }

    @Override
    public TextChanges drop(CharSequence text, Diagram diagram, Set<Object> keys, Object target, Placement placement) {
        MindMap map = map(text, diagram);
        if (map == null || keys.size() != 1 || !(keys.iterator().next() instanceof NodeKey key) || !(target instanceof NodeKey onto)
                || map.node(key) == null || map.node(onto) == null) {
            return TextChanges.of();
        }
        Edit edit = MindMapEdits.move(map, key, onto, MindMapEdits.Placement.valueOf(placement.name()));
        return made(edit, MindMapEdits.MOVE, placement == Placement.INTO ? onto : null);
    }
}
