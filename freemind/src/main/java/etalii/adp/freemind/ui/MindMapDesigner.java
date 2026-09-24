package etalii.adp.freemind.ui;

import java.util.ArrayList;
import java.util.List;

import javax.swing.JComponent;

import org.jetbrains.annotations.NotNull;

import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import etalii.adp.core.AdpDesignerEditor;
import etalii.adp.core.FormatProblem;
import etalii.adp.core.NodeView;
import etalii.adp.freemind.model.ArrowLink;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.parse.MindMapParser;

/**
 * The FreeMind designer: parses the document into a {@link MindMap} and shows it on a
 * {@link MindMapCanvas}. Selection and display-only folding are kept by {@link NodeKey}, so they
 * survive every re-parse, including one that fails in between.
 */
public final class MindMapDesigner extends AdpDesignerEditor<MindMap> {

    /** The context menu group; User Story 2 registers it, and until then there is no menu. */
    public static final String POPUP_GROUP = "etalii.adp.freemind.DesignerPopup";

    private MindMapCanvas canvas;

    public MindMapDesigner(Project project, VirtualFile file, Document document) {
        super(project, file, document);
    }

    @Override
    protected MindMap parse(CharSequence text) throws FormatProblem {
        return MindMapParser.parse(text.toString());
    }

    @Override
    protected JComponent createView() {
        canvas = new MindMapCanvas(this);
        viewState().addSelectionListener(canvas::repaint);
        installActions(canvas, POPUP_GROUP);
        return canvas;
    }

    @Override
    protected void modelChanged(MindMap map) {
        List<Object> kept = new ArrayList<>();
        for (Object key : selection()) {
            if (key instanceof NodeKey nodeKey && map.node(nodeKey) != null) {
                kept.add(key);
            }
        }
        if (kept.size() != selection().size()) {
            viewState().select(kept);
        }
        canvas.relayout();
    }

    /** The canvas the map is drawn on. */
    public MindMapCanvas canvas() {
        return canvas;
    }

    /** Whether a node is drawn collapsed: its {@code FOLDED}, unless folded or unfolded for display only. A leaf never is. */
    public boolean isShownFolded(MapNode node) {
        if (node.children().isEmpty()) {
            return false;
        }
        Boolean override = viewState().foldOverride(node.key());
        return override != null ? override : node.folded();
    }

    /** Collapse or expand for display only, without an edit (read-only files, reveal). */
    public void setShownFolded(MapNode node, boolean folded) {
        viewState().setFoldOverride(node.key(), folded == node.folded() ? null : folded);
        canvas.relayout();
    }

    /** Expand the node's folded ancestors for display only, select it and scroll it into view. */
    @Override
    public void reveal(Object key) {
        MindMap map = model();
        MapNode node = map == null || !(key instanceof NodeKey nodeKey) ? null : map.node(nodeKey);
        if (node == null) {
            return;
        }
        boolean changed = false;
        for (MapNode ancestor = node.parent(); ancestor != null; ancestor = ancestor.parent()) {
            if (isShownFolded(ancestor)) {
                viewState().setFoldOverride(ancestor.key(), ancestor.folded() ? Boolean.FALSE : null);
                changed = true;
            }
        }
        if (changed) {
            canvas.relayout();
        }
        select(List.of(node.key()));
        canvas.scrollTo(node.key());
    }

    @Override
    public NodeView viewOf(Object key) {
        return model() == null ? null : canvas.viewOf(key);
    }

    /** The drawn arrow links, in file order. */
    public List<MindMapLayout.Arrow> arrows() {
        return model() == null ? List.of() : canvas.mapLayout().arrows();
    }

    /** How an arrow link is drawn, or {@code null} when it is not (missing destination, or an end folded away). */
    public MindMapLayout.Arrow arrowOf(ArrowLink link) {
        for (MindMapLayout.Arrow arrow : arrows()) {
            if (arrow.link().equals(link)) {
                return arrow;
            }
        }
        return null;
    }

    @Override
    protected List<?> allKeys() {
        MindMap map = model();
        List<NodeKey> keys = new ArrayList<>();
        if (map != null) {
            collect(map.root(), keys);
        }
        return keys;
    }

    private static void collect(MapNode node, List<NodeKey> keys) {
        keys.add(node.key());
        for (MapNode child : node.children()) {
            collect(child, keys);
        }
    }

    @Override
    protected void zoomChanged() {
        canvas.revalidate();
        canvas.repaint();
    }

    @Override
    public @NotNull StructureViewBuilder getStructureViewBuilder() {
        return new MindMapStructureView(this);
    }

    @Override
    public @NotNull String getName() {
        return "Mind Map";
    }
}
