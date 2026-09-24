package etalii.adp.freemind.ui.handlers;

import java.util.List;
import java.util.Locale;

import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExecutableExtension;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.ui.MindMapEditor;
import etalii.adp.freemind.ui.ViewState;

/**
 * Move Node Up, Move Node Down, Move Under Previous Sibling and Move Up a Level (FR-022), chosen by
 * the parameter after the class name in {@code plugin.xml}: {@code up}, {@code down}, {@code indent}
 * or {@code outdent}. First-level nodes move among the nodes on their own side, as they are drawn.
 * The edit keeps the subtree's text and fixes {@code POSITION} when the first level is crossed.
 */
public class MoveNodeHandler extends NodeHandler implements IExecutableExtension {

    enum Direction {
        UP, DOWN, INDENT, OUTDENT
    }

    private Direction direction = Direction.UP;

    @Override
    public void setInitializationData(IConfigurationElement config, String propertyName, Object data) {
        if (data instanceof String parameter && !parameter.isBlank()) {
            direction = Direction.valueOf(parameter.trim().toUpperCase(Locale.ROOT));
        }
    }

    @Override
    protected boolean enabledFor(MindMapEditor editor, List<MapNode> nodes) {
        return editor.isEditable() && nodes.size() == 1 && target(editor.model(), nodes.get(0)) != null;
    }

    @Override
    protected void run(MindMapEditor editor, List<MapNode> nodes) {
        MapNode node = nodes.get(0);
        MapNode target = target(editor.model(), node);
        Placement placement = switch (direction) {
        case UP -> Placement.BEFORE;
        case DOWN, OUTDENT -> Placement.AFTER;
        case INDENT -> Placement.INTO;
        };
        Edit edit = MindMapEdits.move(editor.model(), node.key(), target.key(), placement);
        if (edit == null) {
            return;
        }
        ViewState view = editor.viewState();
        if (placement == Placement.INTO && view.isShownFolded(target)) {
            view.setShownFolded(target, false); // show where the node went, without another edit
        }
        if (node.id() != null) {
            view.selectAfterRefresh(List.of(node.key())); // a path key names another node after the move
        }
        editor.execute(edit.label(), edit.textEdit());
    }

    /** The node the move is relative to, or {@code null} when this move does not apply. */
    private MapNode target(MindMap map, MapNode node) {
        if (map == null || node.parent() == null) {
            return null;
        }
        return switch (direction) {
        case UP, INDENT -> neighbour(map, node, -1);
        case DOWN -> neighbour(map, node, 1);
        case OUTDENT -> node.depth() >= 2 ? node.parent() : null;
        };
    }

    /** The previous ({@code -1}) or next ({@code 1}) sibling, on the node's own side at the first level. */
    private static MapNode neighbour(MindMap map, MapNode node, int step) {
        List<MapNode> siblings = node.parent().children();
        boolean firstLevel = node.parent().parent() == null;
        for (int i = siblings.indexOf(node) + step; i >= 0 && i < siblings.size(); i += step) {
            MapNode sibling = siblings.get(i);
            if (!firstLevel || map.sideOf(sibling) == map.sideOf(node)) {
                return sibling;
            }
        }
        return null;
    }
}
