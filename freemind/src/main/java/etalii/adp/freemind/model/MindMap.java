package etalii.adp.freemind.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One parse of a FreeMind map: immutable, re-derived after every document change, then discarded.
 * {@code text} is the text it was parsed from, which every range refers to.
 */
public record MindMap(String version, MapNode root, Map<NodeKey, MapNode> nodesByKey, List<ArrowLink> arrowLinks,
        String indentUnit, String lineSeparator, String text) {

    public MindMap {
        nodesByKey = Map.copyOf(nodesByKey);
        arrowLinks = List.copyOf(arrowLinks);
    }

    /** The node with this key, or {@code null}. */
    public MapNode node(NodeKey key) {
        return nodesByKey.get(key);
    }

    /** The node with this {@code ID}, or {@code null}. */
    public MapNode nodeById(String id) {
        return id == null ? null : nodesByKey.get(NodeKey.ofId(id));
    }

    /**
     * The side a first-level branch is drawn on: its {@code POSITION}, or when absent, the side with
     * fewer branches so far in document order, right on a tie (FR-013). {@code null} for the root
     * and deeper nodes.
     */
    public Side sideOf(MapNode node) {
        if (node.parent() != root) {
            return null;
        }
        return automaticSides().get(node.key());
    }

    /** The side a new first-level branch goes on: the one with fewer branches, right on a tie. */
    public Side lighterSide() {
        long left = root.children().stream().filter(n -> sideOf(n) == Side.LEFT).count();
        return left < root.children().size() - left ? Side.LEFT : Side.RIGHT;
    }

    private Map<NodeKey, Side> automaticSides() {
        Map<NodeKey, Side> sides = new HashMap<>();
        int left = 0;
        int right = 0;
        for (MapNode child : root.children()) {
            if (child.side() == Side.LEFT) {
                left++;
            } else if (child.side() == Side.RIGHT) {
                right++;
            }
        }
        for (MapNode child : root.children()) {
            Side side = child.side();
            if (side == null) {
                side = left < right ? Side.LEFT : Side.RIGHT;
                if (side == Side.LEFT) {
                    left++;
                } else {
                    right++;
                }
            }
            sides.put(child.key(), side);
        }
        return sides;
    }
}
