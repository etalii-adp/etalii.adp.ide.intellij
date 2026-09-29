package etalii.adp.freemind.ui.actions;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import javax.swing.text.JTextComponent;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.TestOnly;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Disposer;

import etalii.adp.core.AdpDataKeys;
import etalii.adp.core.AdpToolFileEditor;
import etalii.adp.core.ui.ReadOnlyBanner;
import etalii.adp.freemind.edit.FreeMindConventions;
import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.MindMapFileEditor;

/**
 * Base of the FreeMind editing actions (contracts/plugin-contributions.md). It reads the focused
 * diagram ({@link AdpDataKeys#ADP_TOOL}) and its selected nodes from the action's
 * {@code DataContext}; a subclass says why it does not apply ({@link #disabledReason}) and what it
 * does ({@link #perform}). Outside a diagram the action is disabled, so its shortcut keeps its
 * meaning in text editors. When it is disabled for a reason the user can act on, such as the root
 * or a read-only file, the presentation's description states it, and the status bar and tooltip
 * show it.
 */
public abstract class MindMapAction extends AnAction implements DumbAware {

    public static final String NOTHING_SELECTED = "Select a node first";
    public static final String ONE_NODE = "Select exactly one node";
    public static final String NOT_SHOWN = "The map cannot be shown, so it cannot be edited";
    public static final String ROOT_HAS_NO_SIBLINGS = "The root node has no siblings";
    public static final String ROOT_CANNOT_BE_DELETED = "The root node cannot be deleted";
    public static final String ROOT_CANNOT_BE_MOVED = "The root node cannot be moved";
    public static final String NO_PREVIOUS_SIBLING = "The node has no sibling before it on its side";
    public static final String NO_NEXT_SIBLING = "The node has no sibling after it on its side";
    public static final String FIRST_LEVEL = "The node is already on the first level";
    public static final String NOTHING_TO_FOLD = "The selected nodes have no children to fold";

    /** The text a new node gets; typing it is a separate "Rename Node" edit. */
    public static final String NEW_NODE_TEXT = "New Node";

    private static LongSupplier clock = FreeMindConventions::now;
    private static Supplier<RandomGenerator> random = ThreadLocalRandom::current;

    /**
     * Why the action does not apply to these selected nodes, or {@code null} when it does. Called
     * only with a diagram showing a map, and, unless {@link #needsEditable()} is false, an editable
     * one, with at least one node selected.
     */
    protected abstract String disabledReason(MindMapFileEditor tool, MindMap map, List<MapNode> nodes);

    /** Do the action. Only called when {@link #disabledReason} returned {@code null}. */
    protected abstract void perform(MindMapFileEditor tool, MindMap map, List<MapNode> nodes);

    /** Whether the action edits the file, and so needs a writable one. */
    protected boolean needsEditable() {
        return true;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        MindMapFileEditor tool = tool(event);
        String reason = tool == null ? null : reason(tool);
        presentation.setEnabled(tool != null && reason == null);
        presentation.setDescription(reason != null ? reason : getTemplatePresentation().getDescription());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        MindMapFileEditor tool = tool(event);
        if (tool != null && reason(tool) == null) {
            MindMap map = tool.model();
            perform(tool, map, selectedNodes(tool, map));
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /** Why the action is disabled in this diagram, or {@code null}. */
    private String reason(MindMapFileEditor tool) {
        MindMap map = tool.model();
        if (map == null) {
            return NOT_SHOWN;
        }
        if (needsEditable() && !tool.isEditable()) {
            return ReadOnlyBanner.MESSAGE;
        }
        List<MapNode> nodes = selectedNodes(tool, map);
        return nodes.isEmpty() ? NOTHING_SELECTED : disabledReason(tool, map, nodes);
    }

    /**
     * The focused diagram, or {@code null}. While a text field on its canvas (the in-place editor)
     * has focus, Enter, Space, Delete and Tab are that field's, so there is none.
     */
    private static MindMapFileEditor tool(AnActionEvent event) {
        if (event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) instanceof JTextComponent) {
            return null;
        }
        AdpToolFileEditor<?> tool = event.getData(AdpDataKeys.ADP_TOOL);
        return tool instanceof MindMapFileEditor mindMap ? mindMap : null;
    }

    /** The selected nodes in the latest parse, in selection order. */
    static List<MapNode> selectedNodes(MindMapFileEditor tool, MindMap map) {
        List<MapNode> nodes = new ArrayList<>();
        for (Object key : tool.selection()) {
            MapNode node = key instanceof NodeKey nodeKey ? map.node(nodeKey) : null;
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /** One named step in the IDE's Undo, as a diagram command; {@code andThen} (the selection it leaves) is part of it. */
    static void execute(MindMapFileEditor tool, Edit edit, Runnable andThen) {
        tool.runCommand(edit.label(), text -> edit.changes(), andThen);
    }

    /**
     * The previous ({@code -1}) or next ({@code 1}) sibling, among the nodes on the node's own side
     * on the first level, as they are drawn (spec 001 FR-022), or {@code null}.
     */
    static MapNode neighbour(MindMap map, MapNode node, int step) {
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

    /**
     * Moves the node before, after or into the target as one "Move Node" step, shows a folded
     * target open for display only, and keeps the moved node selected when its key survives the
     * move (an {@code ID}; an index path names another node afterwards).
     */
    static void move(MindMapFileEditor tool, MindMap map, MapNode node, MapNode target, Placement placement) {
        Edit edit = MindMapEdits.move(map, node.key(), target.key(), placement);
        if (edit == null) {
            return;
        }
        if (placement == Placement.INTO && tool.isShownFolded(target)) {
            tool.setShownFolded(target, false);
        }
        execute(tool, edit, () -> tool.select(node.id() != null ? List.of(node.key()) : List.of()));
        if (node.id() != null) {
            tool.canvas().scrollTo(node.key());
        }
    }

    /** The clock read once per edit for {@code CREATED} and {@code MODIFIED}. */
    public static long now() {
        return clock.getAsLong();
    }

    /** The random source for one new node's {@code ID}. */
    public static RandomGenerator random() {
        return random.get();
    }

    /**
     * Use a fixed clock and random source until {@code parent} is disposed, so the reference
     * scenarios run through the diagram give their recorded bytes.
     */
    @TestOnly
    public static void useClock(LongSupplier fixedClock, Supplier<RandomGenerator> fixedRandom, Disposable parent) {
        clock = fixedClock;
        random = fixedRandom;
        Disposer.register(parent, () -> {
            clock = FreeMindConventions::now;
            random = ThreadLocalRandom::current;
        });
    }
}
