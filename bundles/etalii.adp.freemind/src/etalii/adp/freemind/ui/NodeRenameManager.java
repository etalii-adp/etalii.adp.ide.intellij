package etalii.adp.freemind.ui;

import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.tools.CellEditorLocator;
import org.eclipse.gef.tools.DirectEditManager;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;

/**
 * Edits a node's text in place (FR-019, FR-020): a text field over the node's figure, committed by
 * Enter or by leaving it, cancelled by Escape. A changed text becomes one "Rename Node" edit; a rich
 * node's formatting is only replaced by plain text after the user confirms it.
 */
public class NodeRenameManager extends DirectEditManager {

    static final String TITLE = "Rename Node";
    static final String RICH_WARNING = "This node's text is formatted. Renaming it replaces the formatting with plain text.";

    /** Asks the user a yes/no question; tests replace it so no dialog opens. */
    @FunctionalInterface
    interface Confirmer {
        boolean confirm(Shell shell, String title, String message);
    }

    static Confirmer confirmer = MessageDialog::openConfirm;

    private final MindMapEditor editor;
    private final NodeKey key;
    private String initialText = "";
    private boolean finished;

    private NodeRenameManager(MindMapEditor editor, GraphicalEditPart part) {
        super(part, TextCellEditor.class, new FigureLocator(part.getFigure()));
        this.editor = editor;
        this.key = (NodeKey) part.getModel();
    }

    /** Open the in-place editor on the node, when it is drawn. */
    public static void open(MindMapEditor editor, NodeKey key) {
        EditPart part = editor.viewer().getEditPartRegistry().get(key);
        if (part instanceof GraphicalEditPart graphical) {
            editor.viewer().flush();
            new NodeRenameManager(editor, graphical).show();
        }
    }

    @Override
    protected CellEditor createCellEditorOn(Composite composite) {
        return new TextCellEditor(composite, SWT.SINGLE);
    }

    @Override
    protected void initCellEditor() {
        MapNode node = currentNode();
        initialText = node == null || node.text() == null ? "" : node.text();
        getCellEditor().setValue(initialText);
        if (getCellEditor().getControl() instanceof Text text) {
            text.selectAll();
        }
    }

    @Override
    protected void commit() {
        if (finished) {
            return;
        }
        finished = true;
        String text = getCellEditor() == null ? null : (String) getCellEditor().getValue();
        bringDown();
        if (text != null && !text.equals(initialText)) {
            rename(text);
        }
    }

    private void rename(String text) {
        MapNode node = currentNode();
        if (node == null || !editor.isEditable()) {
            return;
        }
        if (node.rich() && !confirmer.confirm(editor.getSite().getShell(), TITLE, RICH_WARNING)) {
            return;
        }
        editor.execute(MindMapEdits.RENAME, MindMapEdits.rename(editor.model(), key, text).textEdit());
    }

    private MapNode currentNode() {
        MindMap map = editor.model();
        return map == null ? null : map.node(key);
    }

    /** Places the text field over the figure, at least as wide as its text. */
    private record FigureLocator(IFigure figure) implements CellEditorLocator {

        @Override
        public void relocate(CellEditor cellEditor) {
            if (!(cellEditor.getControl() instanceof Text text)) {
                return;
            }
            Rectangle bounds = figure.getBounds().getCopy();
            figure.translateToAbsolute(bounds);
            Point size = text.computeSize(SWT.DEFAULT, SWT.DEFAULT);
            text.setBounds(bounds.x, bounds.y, Math.max(bounds.width, size.x + 16), Math.max(bounds.height, size.y));
        }
    }
}
