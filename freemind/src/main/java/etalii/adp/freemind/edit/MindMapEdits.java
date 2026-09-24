package etalii.adp.freemind.edit;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

import etalii.adp.core.TextChange;
import etalii.adp.core.TextChanges;
import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.AttributeRange;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.model.NodeRanges;
import etalii.adp.freemind.model.Side;

/**
 * The edit catalogue (data-model.md): each action is {@link TextChanges} against the text the map
 * was parsed from, touching only the ranges it must, plus the label shown in the Edit menu. New and
 * changed content follows FreeMind 1.0.1's own conventions (research R5).
 */
public final class MindMapEdits {

    public static final String ADD_CHILD = "Add Child Node";
    public static final String ADD_SIBLING = "Add Sibling Node";
    public static final String RENAME = "Rename Node";
    public static final String DELETE_NODE = "Delete Node";
    public static final String DELETE_NODES = "Delete Nodes";
    public static final String MOVE = "Move Node";
    public static final String FOLD = "Fold Branch";
    public static final String UNFOLD = "Unfold Branch";

    /** One undoable change. {@code created} is the key of an added node, else {@code null}. */
    public record Edit(String label, TextChanges changes, NodeKey created) {
    }

    /** Where a moved node goes relative to the target node. */
    public enum Placement {
        BEFORE, AFTER, INTO
    }

    private MindMapEdits() {
    }

    public static Edit addChild(MindMap map, NodeKey parentKey, String text) {
        return addChild(map, parentKey, text, FreeMindConventions.now(), ThreadLocalRandom.current());
    }

    /** A new last child. The parent gets a new {@code MODIFIED}; a first-level child a side. */
    public static Edit addChild(MindMap map, NodeKey parentKey, String text, long now, RandomGenerator random) {
        MapNode parent = existing(map, parentKey);
        String id = FreeMindConventions.newId(candidate -> map.nodeById(candidate) != null, random);
        Side side = parent == map.root() ? map.lighterSide() : null;
        List<TextChange> edit = new ArrayList<>();
        setAttributes(edit, map, parent, Map.of("MODIFIED", Long.toString(now)));
        edit.add(insertInto(map, parent, newNode(id, now, side, text)));
        return new Edit(ADD_CHILD, new TextChanges(edit), NodeKey.ofId(id));
    }

    public static Edit addSibling(MindMap map, NodeKey siblingKey, String text) {
        return addSibling(map, siblingKey, text, FreeMindConventions.now(), ThreadLocalRandom.current());
    }

    /** A new node right after the given one; on the first level, on the same side. */
    public static Edit addSibling(MindMap map, NodeKey siblingKey, String text, long now, RandomGenerator random) {
        MapNode sibling = existing(map, siblingKey);
        if (sibling == map.root()) {
            throw new IllegalArgumentException("The root node has no siblings");
        }
        String id = FreeMindConventions.newId(candidate -> map.nodeById(candidate) != null, random);
        TextChange insert = insertBeside(map, sibling, Placement.AFTER, newNode(id, now, map.sideOf(sibling), text));
        return new Edit(ADD_SIBLING, TextChanges.of(insert), NodeKey.ofId(id));
    }

    public static Edit rename(MindMap map, NodeKey key, String text) {
        return rename(map, key, text, FreeMindConventions.now());
    }

    /**
     * Sets the node's text and {@code MODIFIED}. A rich node loses its {@code richcontent
     * TYPE="NODE"} element for a {@code TEXT} attribute; the caller confirms that first (FR-020).
     */
    public static Edit rename(MindMap map, NodeKey key, String text, long now) {
        MapNode node = existing(map, key);
        List<TextChange> edit = new ArrayList<>();
        if (node.ranges().richNode() != null) {
            edit.add(TextChange.delete(node.ranges().richNode().offset(), node.ranges().richNode().length()));
        }
        setAttributes(edit, map, node, Map.of("MODIFIED", Long.toString(now), "TEXT", text));
        return new Edit(RENAME, new TextChanges(edit), null);
    }

    /**
     * Removes each node with its subtree, and every arrow link elsewhere that points into a removed
     * subtree (FR-021).
     */
    public static Edit delete(MindMap map, Collection<NodeKey> keys) {
        List<MapNode> selected = new ArrayList<>();
        for (NodeKey key : keys) {
            MapNode node = existing(map, key);
            if (node == map.root()) {
                throw new IllegalArgumentException("The root node cannot be deleted");
            }
            selected.add(node);
        }
        List<MapNode> topmost = selected.stream().filter(n -> selected.stream().noneMatch(o -> o != n && o.contains(n))).toList();
        Set<String> removedIds = new HashSet<>();
        List<TextChange> edit = new ArrayList<>();
        for (MapNode node : topmost) {
            collectIds(node, removedIds);
            edit.add(TextChange.delete(node.ranges().element().offset(), node.ranges().element().length()));
        }
        for (ArrowLink link : map.arrowLinks()) {
            boolean insideRemoved = topmost.stream().anyMatch(n -> n.ranges().element().covers(link.range()));
            if (!insideRemoved && removedIds.contains(link.destinationId())) {
                edit.add(TextChange.delete(link.range().offset(), link.range().length()));
            }
        }
        return new Edit(selected.size() == 1 ? DELETE_NODE : DELETE_NODES, new TextChanges(edit), null);
    }

    /**
     * Moves a node with its subtree before, after or into the target, taking its text verbatim
     * (research R5). Moving onto the first level adds {@code POSITION}; leaving it removes it.
     * Returns {@code null} for the root, for a target inside the moved subtree, and for a move that
     * changes nothing.
     */
    public static Edit move(MindMap map, NodeKey key, NodeKey targetKey, Placement placement) {
        MapNode node = existing(map, key);
        MapNode target = existing(map, targetKey);
        if (node == map.root() || node.contains(target) || (placement != Placement.INTO && target == map.root())) {
            return null;
        }
        MapNode newParent = placement == Placement.INTO ? target : target.parent();
        int insertAt = insertionPoint(target, placement);
        NodeRanges ranges = node.ranges();
        if (insertAt >= ranges.element().offset() && insertAt <= ranges.element().end()) {
            return null;
        }

        Side side = null;
        if (newParent == map.root()) {
            side = placement == Placement.INTO ? map.lighterSide() : map.sideOf(target);
        }
        String moved = movedText(map, node, side);
        List<TextChange> edit = new ArrayList<>();
        edit.add(TextChange.delete(ranges.element().offset(), ranges.element().length()));
        edit.add(placement == Placement.INTO ? insertInto(map, target, moved) : insertBeside(map, target, placement, moved));
        return new Edit(MOVE, new TextChanges(edit), null);
    }

    /**
     * Inserts {@code FOLDED="true"} or removes the attribute (FR-023). Returns {@code null} when the
     * node is already in that state, or has no children to fold.
     */
    public static Edit setFolded(MindMap map, NodeKey key, boolean folded) {
        MapNode node = existing(map, key);
        if (node.folded() == folded || (folded && node.children().isEmpty())) {
            return null;
        }
        List<TextChange> edit = new ArrayList<>();
        AttributeRange attribute = node.ranges().attribute("FOLDED");
        if (folded) {
            setAttributes(edit, map, node, Map.of("FOLDED", "true"));
        } else {
            edit.add(TextChange.delete(attribute.start(), attribute.end() - attribute.start()));
        }
        return new Edit(folded ? FOLD : UNFOLD, new TextChanges(edit), null);
    }

    private static MapNode existing(MindMap map, NodeKey key) {
        MapNode node = map.node(key);
        if (node == null) {
            throw new IllegalArgumentException("No node " + key + " in the map");
        }
        return node;
    }

    private static void collectIds(MapNode node, Set<String> ids) {
        if (node.id() != null) {
            ids.add(node.id());
        }
        for (MapNode child : node.children()) {
            collectIds(child, ids);
        }
    }

    private static String newNode(String id, long now, Side side, String text) {
        StringBuilder node = new StringBuilder("<node CREATED=\"").append(now).append("\" ID=\"").append(id).append("\" MODIFIED=\"")
                .append(now).append('"');
        if (side != null) {
            node.append(" POSITION=\"").append(side.attributeValue()).append('"');
        }
        return node.append(" TEXT=\"").append(FreeMindConventions.escape(text)).append("\"/>").toString();
    }

    /**
     * The element's text without the whitespace and separator that made it a whole line, with
     * {@code POSITION} set to {@code side}, or removed when {@code side} is {@code null}.
     */
    private static String movedText(MindMap map, MapNode node, Side side) {
        NodeRanges ranges = node.ranges();
        int start = ranges.startTag().offset();
        int end = ranges.element().end();
        String text = map.text();
        while (end > start && (text.charAt(end - 1) == '\n' || text.charAt(end - 1) == '\r' || text.charAt(end - 1) == ' '
                || text.charAt(end - 1) == '\t')) {
            end--;
        }
        StringBuilder moved = new StringBuilder(text.substring(start, end));
        AttributeRange position = ranges.attribute("POSITION");
        if (side == null && position != null) {
            moved.delete(position.start() - start, position.end() - start);
        } else if (side != null && position == null) {
            moved.insert(insertionOffset(ranges, "POSITION") - start, " POSITION=\"" + side.attributeValue() + "\"");
        } else if (side != null) {
            moved.replace(position.value().offset() - start, position.value().end() - start, side.attributeValue());
        }
        return moved.toString();
    }

    private static int insertionPoint(MapNode target, Placement placement) {
        NodeRanges ranges = target.ranges();
        return switch (placement) {
        case BEFORE -> ranges.element().offset();
        case AFTER -> ranges.element().end();
        case INTO -> ranges.childInsertPoint();
        };
    }

    /** Inserts {@code element} before or after {@code target}, in the target's line style. */
    private static TextChange insertBeside(MindMap map, MapNode target, Placement placement, String element) {
        NodeRanges ranges = target.ranges();
        String separator = map.lineSeparator();
        if (!ranges.ownLine()) {
            return TextChange.insert(placement == Placement.BEFORE ? ranges.element().offset() : ranges.element().end(), element);
        }
        if (placement == Placement.BEFORE) {
            return TextChange.insert(ranges.element().offset(), ranges.indent() + element + separator);
        }
        int end = ranges.element().end();
        boolean endsWithBreak = end > 0 && (map.text().charAt(end - 1) == '\n' || map.text().charAt(end - 1) == '\r');
        return TextChange.insert(end, endsWithBreak ? ranges.indent() + element + separator : separator + ranges.indent() + element);
    }

    /**
     * Inserts {@code element} as the parent's last child, indented like the last child, or like the
     * parent plus the file's indent unit. A self-closing parent's {@code />} becomes {@code >},
     * the child and {@code </node>}.
     */
    private static TextChange insertInto(MindMap map, MapNode parent, String element) {
        NodeRanges ranges = parent.ranges();
        String separator = map.lineSeparator();
        List<MapNode> children = parent.children();
        String indent = !children.isEmpty() && children.get(children.size() - 1).ranges().ownLine()
                ? children.get(children.size() - 1).ranges().indent()
                : ranges.indent() + map.indentUnit();
        if (ranges.selfClosing()) {
            String replacement = ranges.ownLine() ? ">" + separator + indent + element + separator + ranges.indent() + "</node>"
                    : ">" + element + "</node>";
            return TextChange.replace(ranges.childInsertPoint(), 2, replacement);
        }
        if (ranges.childInsertOwnLine()) {
            return TextChange.insert(ranges.childInsertPoint(), indent + element + separator);
        }
        return TextChange.insert(ranges.childInsertPoint(), element);
    }

    /**
     * Sets attribute values on the node's start tag: replacing the values of present attributes, and
     * inserting absent ones where alphabetical order puts them, as FreeMind writes them.
     */
    private static void setAttributes(List<TextChange> edit, MindMap map, MapNode node, Map<String, String> values) {
        NodeRanges ranges = node.ranges();
        Map<Integer, StringBuilder> insertions = new TreeMap<>();
        for (Map.Entry<String, String> entry : new TreeMap<>(values).entrySet()) {
            String escaped = FreeMindConventions.escape(entry.getValue());
            AttributeRange present = ranges.attribute(entry.getKey());
            if (present != null) {
                edit.add(TextChange.replace(present.value().offset(), present.value().length(), escaped));
            } else {
                insertions.computeIfAbsent(insertionOffset(ranges, entry.getKey()), offset -> new StringBuilder())
                        .append(' ').append(entry.getKey()).append("=\"").append(escaped).append('"');
            }
        }
        insertions.forEach((offset, text) -> edit.add(TextChange.insert(offset, text.toString())));
    }

    /** Before the first attribute whose name sorts after {@code name}, else after the last one. */
    private static int insertionOffset(NodeRanges ranges, String name) {
        for (Map.Entry<String, AttributeRange> attribute : ranges.attributes().entrySet()) {
            if (attribute.getKey().compareTo(name) > 0) {
                return attribute.getValue().start();
            }
        }
        return ranges.attributeInsertPoint();
    }
}
