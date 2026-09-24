package etalii.adp.freemind.model;

import java.util.List;

import org.eclipse.swt.graphics.RGB;

/**
 * One {@code node} element, as FreeMind 1.0.1 displays it. Attributes, clouds, edges, hooks and
 * unknown content are not fields: they stay in the text inside {@link NodeRanges#element()}.
 */
public final class MapNode {

    private final NodeKey key;
    private final String id;
    private final String text;
    private final boolean rich;
    private final Long created;
    private final Long modified;
    private final Side side;
    private final boolean folded;
    private final List<String> icons;
    private final RGB color;
    private final RGB backgroundColor;
    private final FontSpec font;
    private final String link;
    private final String note;
    private final int hgap;
    private final int vgap;
    private final int vshift;
    private final List<MapNode> children;
    private final NodeRanges ranges;
    private MapNode parent;

    /** Children are built first; this constructor makes itself their parent. */
    public MapNode(NodeKey key, String id, String text, boolean rich, Long created, Long modified, Side side, boolean folded,
            List<String> icons, RGB color, RGB backgroundColor, FontSpec font, String link, String note, int hgap, int vgap,
            int vshift, List<MapNode> children, NodeRanges ranges) {
        this.key = key;
        this.id = id;
        this.text = text;
        this.rich = rich;
        this.created = created;
        this.modified = modified;
        this.side = side;
        this.folded = folded;
        this.icons = List.copyOf(icons);
        this.color = color;
        this.backgroundColor = backgroundColor;
        this.font = font;
        this.link = link;
        this.note = note;
        this.hgap = hgap;
        this.vgap = vgap;
        this.vshift = vshift;
        this.children = List.copyOf(children);
        this.ranges = ranges;
        for (MapNode child : this.children) {
            child.parent = this;
        }
    }

    public NodeKey key() {
        return key;
    }

    /** The {@code ID}, or {@code null} for maps written before FreeMind 0.8. */
    public String id() {
        return id;
    }

    /** The display text: {@code TEXT}, or the rich node content as plain text. */
    public String text() {
        return text;
    }

    /** The text comes from a {@code richcontent TYPE="NODE"} element. */
    public boolean rich() {
        return rich;
    }

    public Long created() {
        return created;
    }

    public Long modified() {
        return modified;
    }

    /** The recorded {@code POSITION}; see {@link MindMap#sideOf(MapNode)} for the side drawn. */
    public Side side() {
        return side;
    }

    public boolean folded() {
        return folded;
    }

    public List<String> icons() {
        return icons;
    }

    public RGB color() {
        return color;
    }

    public RGB backgroundColor() {
        return backgroundColor;
    }

    public FontSpec font() {
        return font;
    }

    public String link() {
        return link;
    }

    /** The note as plain text, or {@code null}. */
    public String note() {
        return note;
    }

    public int hgap() {
        return hgap;
    }

    public int vgap() {
        return vgap;
    }

    public int vshift() {
        return vshift;
    }

    public List<MapNode> children() {
        return children;
    }

    /** {@code null} for the root. */
    public MapNode parent() {
        return parent;
    }

    public NodeRanges ranges() {
        return ranges;
    }

    /** Number of ancestors: 0 for the root, 1 for a first-level branch. */
    public int depth() {
        int depth = 0;
        for (MapNode n = parent; n != null; n = n.parent) {
            depth++;
        }
        return depth;
    }

    /** True when {@code other} is this node or one of its descendants. */
    public boolean contains(MapNode other) {
        for (MapNode n = other; n != null; n = n.parent) {
            if (n == this) {
                return true;
            }
        }
        return false;
    }

    @Override
    public String toString() {
        return key + " \"" + text + "\"";
    }
}
