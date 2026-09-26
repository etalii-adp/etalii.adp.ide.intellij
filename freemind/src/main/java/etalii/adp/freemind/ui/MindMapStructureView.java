package etalii.adp.freemind.ui;

import java.util.List;

import etalii.adp.core.AdpStructureView;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;

/** The node tree in the Structure view: each node's text after its first icon, children in document order (FR-015). */
public final class MindMapStructureView extends AdpStructureView {

    private final MindMapDesigner designer;

    public MindMapStructureView(MindMapDesigner designer) {
        super(designer);
        this.designer = designer;
    }

    @Override
    protected Object rootKey() {
        MindMap map = designer.model();
        return map == null ? null : map.root().key();
    }

    @Override
    protected String text(Object key) {
        MapNode node = node(key);
        if (node == null) {
            return "";
        }
        return node.icons().isEmpty() ? node.text() : FreeMindIcons.display(node.icons().get(0)) + " " + node.text();
    }

    @Override
    protected List<?> children(Object key) {
        MapNode node = node(key);
        return node == null ? List.of() : node.children().stream().map(MapNode::key).toList();
    }

    private MapNode node(Object key) {
        MindMap map = designer.model();
        return map == null || !(key instanceof NodeKey nodeKey) ? null : map.node(nodeKey);
    }
}
