package etalii.adp.freemind.ui;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;

/**
 * The GEF contents of the visual page: one object for the editor's lifetime, holding the latest
 * parse, the transient fold overrides (research R7) and a selection to restore by key after the
 * next redraw. Keeping it stable lets the framework refresh existing edit parts in place.
 */
public final class ViewState {

    private MindMap map;
    private final Set<NodeKey> expanded = new HashSet<>();
    private final Set<NodeKey> collapsed = new HashSet<>();
    private List<NodeKey> pendingSelection;
    private Map<NodeKey, List<ArrowLink>> linksBySource = Map.of();
    private Map<String, List<ArrowLink>> linksByDestination = Map.of();

    /** The latest parse, or {@code null} before the first. */
    public MindMap map() {
        return map;
    }

    void setMap(MindMap map) {
        this.map = map;
        Map<NodeKey, List<ArrowLink>> bySource = new HashMap<>();
        Map<String, List<ArrowLink>> byDestination = new HashMap<>();
        for (ArrowLink link : map.arrowLinks()) {
            if (map.nodeById(link.destinationId()) != null) {
                bySource.computeIfAbsent(link.source(), k -> new ArrayList<>()).add(link);
                byDestination.computeIfAbsent(link.destinationId(), k -> new ArrayList<>()).add(link);
            }
        }
        linksBySource = bySource;
        linksByDestination = byDestination;
    }

    /** Whether the node is drawn collapsed: its {@code FOLDED}, unless overridden for display. */
    public boolean isShownFolded(MapNode node) {
        if (node.children().isEmpty()) {
            return false;
        }
        return collapsed.contains(node.key()) || node.folded() && !expanded.contains(node.key());
    }

    /** Collapse or expand for display only, without an edit (read-only files, reveal). */
    public void setShownFolded(MapNode node, boolean folded) {
        clearOverride(node.key());
        if (folded && !node.folded()) {
            collapsed.add(node.key());
        } else if (!folded && node.folded()) {
            expanded.add(node.key());
        }
    }

    /** Drop any display-only override, so the file's {@code FOLDED} applies again. */
    public void clearOverride(NodeKey key) {
        expanded.remove(key);
        collapsed.remove(key);
    }

    /** True when every ancestor is shown unfolded. */
    public boolean isVisible(MapNode node) {
        for (MapNode n = node.parent(); n != null; n = n.parent()) {
            if (isShownFolded(n)) {
                return false;
            }
        }
        return true;
    }

    /** The keys of all drawn nodes, in document order. */
    public List<NodeKey> visibleKeys() {
        List<NodeKey> keys = new ArrayList<>();
        if (map != null) {
            collectVisible(map.root(), keys);
        }
        return keys;
    }

    private void collectVisible(MapNode node, List<NodeKey> keys) {
        keys.add(node.key());
        if (!isShownFolded(node)) {
            for (MapNode child : node.children()) {
                collectVisible(child, keys);
            }
        }
    }

    /** The drawn arrow links leaving this node: destination present and both ends visible. */
    public List<ArrowLink> linksFrom(MapNode node) {
        return drawn(linksBySource.getOrDefault(node.key(), List.of()));
    }

    /** The drawn arrow links arriving at this node. */
    public List<ArrowLink> linksTo(MapNode node) {
        return node.id() == null ? List.of() : drawn(linksByDestination.getOrDefault(node.id(), List.of()));
    }

    private List<ArrowLink> drawn(List<ArrowLink> links) {
        if (links.isEmpty()) {
            return links;
        }
        List<ArrowLink> drawn = new ArrayList<>(links.size());
        for (ArrowLink link : links) {
            MapNode source = map.node(link.source());
            MapNode destination = map.nodeById(link.destinationId());
            if (source != null && destination != null && isVisible(source) && isVisible(destination)) {
                drawn.add(link);
            }
        }
        return drawn;
    }

    /** Select these nodes once the next redraw has created their parts. */
    public void selectAfterRefresh(Collection<NodeKey> keys) {
        pendingSelection = List.copyOf(keys);
    }

    /** The selection requested by {@link #selectAfterRefresh}, once; then {@code null}. */
    public List<NodeKey> takePendingSelection() {
        List<NodeKey> keys = pendingSelection;
        pendingSelection = null;
        return keys;
    }
}
