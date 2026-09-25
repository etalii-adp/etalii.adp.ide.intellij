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
import etalii.adp.core.AdpDesignerEditor;
import etalii.adp.core.ui.ReadOnlyBanner;
import etalii.adp.freemind.edit.FreeMindConventions;
import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.MindMapDesigner;

/**
 * Base of the FreeMind editing actions (contracts/plugin-contributions.md). It reads the focused
 * designer ({@link AdpDataKeys#ADP_DESIGNER}) and its selected nodes from the action's
 * {@code DataContext}; a subclass says why it does not apply ({@link #disabledReason}) and what it
 * does ({@link #perform}). Outside a designer the action is disabled, so its shortcut keeps its
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
     * only with a designer showing a map, and, unless {@link #needsEditable()} is false, an editable
     * one, with at least one node selected.
     */
    protected abstract String disabledReason(MindMapDesigner designer, MindMap map, List<MapNode> nodes);

    /** Do the action. Only called when {@link #disabledReason} returned {@code null}. */
    protected abstract void perform(MindMapDesigner designer, MindMap map, List<MapNode> nodes);

    /** Whether the action edits the file, and so needs a writable one. */
    protected boolean needsEditable() {
        return true;
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        Presentation presentation = event.getPresentation();
        MindMapDesigner designer = designer(event);
        String reason = designer == null ? null : reason(designer);
        presentation.setEnabled(designer != null && reason == null);
        presentation.setDescription(reason != null ? reason : getTemplatePresentation().getDescription());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent event) {
        MindMapDesigner designer = designer(event);
        if (designer != null && reason(designer) == null) {
            MindMap map = designer.model();
            perform(designer, map, selectedNodes(designer, map));
        }
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.EDT;
    }

    /** Why the action is disabled in this designer, or {@code null}. */
    private String reason(MindMapDesigner designer) {
        MindMap map = designer.model();
        if (map == null) {
            return NOT_SHOWN;
        }
        if (needsEditable() && !designer.isEditable()) {
            return ReadOnlyBanner.MESSAGE;
        }
        List<MapNode> nodes = selectedNodes(designer, map);
        return nodes.isEmpty() ? NOTHING_SELECTED : disabledReason(designer, map, nodes);
    }

    /**
     * The focused designer, or {@code null}. While a text field on its canvas (the in-place editor)
     * has focus, Enter, Space, Delete and Tab are that field's, so there is none.
     */
    private static MindMapDesigner designer(AnActionEvent event) {
        if (event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) instanceof JTextComponent) {
            return null;
        }
        AdpDesignerEditor<?> designer = event.getData(AdpDataKeys.ADP_DESIGNER);
        return designer instanceof MindMapDesigner mindMap ? mindMap : null;
    }

    /** The selected nodes in the latest parse, in selection order. */
    static List<MapNode> selectedNodes(MindMapDesigner designer, MindMap map) {
        List<MapNode> nodes = new ArrayList<>();
        for (Object key : designer.selection()) {
            MapNode node = key instanceof NodeKey nodeKey ? map.node(nodeKey) : null;
            if (node != null) {
                nodes.add(node);
            }
        }
        return nodes;
    }

    /** One named step in the IDE's Undo; {@code andThen} (the selection it leaves) is part of it. */
    static void execute(MindMapDesigner designer, Edit edit, Runnable andThen) {
        designer.execute(edit.label(), edit.changes(), andThen);
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
    static void move(MindMapDesigner designer, MindMap map, MapNode node, MapNode target, Placement placement) {
        Edit edit = MindMapEdits.move(map, node.key(), target.key(), placement);
        if (edit == null) {
            return;
        }
        if (placement == Placement.INTO && designer.isShownFolded(target)) {
            designer.setShownFolded(target, false);
        }
        execute(designer, edit, () -> designer.select(node.id() != null ? List.of(node.key()) : List.of()));
        if (node.id() != null) {
            designer.canvas().scrollTo(node.key());
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
     * scenarios run through the designer give their recorded bytes.
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
