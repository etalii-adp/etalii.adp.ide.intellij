package etalii.adp.freemind.ui;

import java.util.List;

import org.eclipse.gef.EditPart;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.views.contentoutline.ContentOutlinePage;

import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;

/**
 * The map's node tree in the standard Outline view (FR-026). Its elements are {@link NodeKey}s,
 * refreshed after each re-parse. Selecting a node here reveals and selects it in the designer
 * without an edit; selecting in the designer selects it here.
 */
public class MindMapOutlinePage extends ContentOutlinePage {

    private final MindMapEditor editor;
    private final IDocumentListener documentListener = new IDocumentListener() {
        @Override
        public void documentAboutToBeChanged(DocumentEvent event) {
        }

        @Override
        public void documentChanged(DocumentEvent event) {
            // queued after the editor's own re-parse, which registered its listener first
            if (getControl() != null && !getControl().isDisposed()) {
                getControl().getDisplay().asyncExec(MindMapOutlinePage.this::refresh);
            }
        }
    };
    private final ISelectionChangedListener designerListener = event -> showDesignerSelection();
    private IDocument document;
    private boolean linking;

    public MindMapOutlinePage(MindMapEditor editor) {
        this.editor = editor;
    }

    @Override
    public void createControl(Composite parent) {
        super.createControl(parent);
        TreeViewer tree = getTreeViewer();
        tree.setContentProvider(new NodeContent());
        tree.setLabelProvider(new LabelProvider() {
            @Override
            public String getText(Object element) {
                MapNode node = node(element);
                return node == null ? "" : node.text().lines().findFirst().orElse("");
            }
        });
        tree.setUseHashlookup(true);
        tree.setInput(editor);
        tree.expandToLevel(2);
        document = editor.document();
        document.addDocumentListener(documentListener);
        editor.viewer().addSelectionChangedListener(designerListener);
        showDesignerSelection();
    }

    /** The node tree; public so tests and other views can reach it. */
    @Override
    public TreeViewer getTreeViewer() {
        return super.getTreeViewer();
    }

    private void refresh() {
        TreeViewer tree = getTreeViewer();
        if (tree != null && !tree.getControl().isDisposed()) {
            tree.refresh();
        }
    }

    @Override
    public void selectionChanged(SelectionChangedEvent event) {
        super.selectionChanged(event);
        if (linking) {
            return;
        }
        if (event.getStructuredSelection().getFirstElement() instanceof NodeKey key) {
            linking = true;
            try {
                editor.reveal(key);
            } finally {
                linking = false;
            }
        }
    }

    private void showDesignerSelection() {
        TreeViewer tree = getTreeViewer();
        if (linking || tree == null || tree.getControl().isDisposed()) {
            return;
        }
        List<Object> keys = editor.viewer().getSelectedEditParts().stream().map(EditPart::getModel)
                .filter(NodeKey.class::isInstance).map(Object.class::cast).toList();
        linking = true;
        try {
            tree.setSelection(new StructuredSelection(keys), true);
        } finally {
            linking = false;
        }
    }

    @Override
    public void dispose() {
        if (document != null) {
            document.removeDocumentListener(documentListener);
        }
        if (editor.viewer() != null && editor.viewer().getControl() != null && !editor.viewer().getControl().isDisposed()) {
            editor.viewer().removeSelectionChangedListener(designerListener);
        }
        super.dispose();
    }

    private MapNode node(Object element) {
        MindMap map = editor.model();
        return map == null || !(element instanceof NodeKey key) ? null : map.node(key);
    }

    private final class NodeContent implements ITreeContentProvider {
        @Override
        public Object[] getElements(Object input) {
            MindMap map = editor.model();
            return map == null ? new Object[0] : new Object[] { map.root().key() };
        }

        @Override
        public Object[] getChildren(Object element) {
            MapNode node = node(element);
            return node == null ? new Object[0] : node.children().stream().map(MapNode::key).toArray();
        }

        @Override
        public Object getParent(Object element) {
            MapNode node = node(element);
            return node == null || node.parent() == null ? null : node.parent().key();
        }

        @Override
        public boolean hasChildren(Object element) {
            MapNode node = node(element);
            return node != null && !node.children().isEmpty();
        }
    }
}
