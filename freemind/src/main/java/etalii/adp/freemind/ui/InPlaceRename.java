package etalii.adp.freemind.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CustomShortcutSet;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.components.JBTextField;
import com.intellij.ui.scale.JBUIScale;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.ui.actions.MindMapAction;

/**
 * Edits a node's text in place (spec 001 FR-019, FR-020): a text field over the node's box on the
 * canvas, starting from its text. Enter commits, Escape and leaving the field cancel. A changed
 * text becomes one "Rename Node" step; a rich node's formatting is only replaced by plain text
 * after the user confirms it. At most one is open per canvas.
 */
public final class InPlaceRename {

    public static final String TITLE = "Rename Node";
    public static final String RICH_WARNING = "This node's text is formatted. Renaming it replaces the formatting with plain text.";

    private static final String OPEN = "etalii.adp.freemind.InPlaceRename";

    private final MindMapDesigner designer;
    private final MindMapCanvas canvas;
    private final NodeKey key;
    private final String initialText;
    private final JBTextField field;
    private final Runnable modelListener = this::modelChanged;
    private boolean finished;

    private InPlaceRename(MindMapDesigner designer, MapNode node, Rectangle box) {
        this.designer = designer;
        this.canvas = designer.canvas();
        this.key = node.key();
        this.initialText = node.text() == null ? "" : node.text();
        this.field = new JBTextField(initialText);
        double zoom = designer.viewState().zoom();
        field.setFont(field.getFont().deriveFont((float) (field.getFont().getSize2D() * zoom)));
        Dimension preferred = field.getPreferredSize();
        field.setBounds(box.x, box.y, Math.max(box.width, preferred.width + JBUIScale.scale(16)), Math.max(box.height, preferred.height));
        field.addActionListener(event -> commit());
        field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    e.consume();
                    cancel();
                }
            }
        });
        // Escape is also an IDE shortcut; one registered on the field itself comes first.
        new DumbAwareAction() {
            @Override
            public void actionPerformed(@NotNull AnActionEvent event) {
                cancel();
            }
        }.registerCustomShortcutSet(CustomShortcutSet.fromString("ESCAPE"), field);
        field.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                if (!e.isTemporary()) {
                    cancel();
                }
            }
        });
    }

    /**
     * Open the in-place editor on the node, first bringing it into view when a folded branch hides
     * it. Nothing happens when the file cannot be edited or the node is not in the map.
     */
    public static void open(MindMapDesigner designer, NodeKey key) {
        MindMap map = designer.model();
        MapNode node = map == null ? null : map.node(key);
        if (node == null || !designer.isEditable()) {
            return;
        }
        MindMapCanvas canvas = designer.canvas();
        if (canvas.getClientProperty(OPEN) instanceof InPlaceRename open) {
            open.cancel();
        }
        if (canvas.viewOf(key) == null) {
            designer.reveal(key);
        } else {
            canvas.scrollTo(key);
        }
        Rectangle box = canvas.boundsOf(key);
        if (box != null) {
            new InPlaceRename(designer, node, box).show();
        }
    }

    /** Whether an in-place editor is open on this canvas. */
    public static boolean isOpen(MindMapCanvas canvas) {
        return canvas.getClientProperty(OPEN) != null;
    }

    private void show() {
        canvas.putClientProperty(OPEN, this);
        canvas.add(field);
        designer.addModelListener(modelListener);
        canvas.revalidate();
        canvas.repaint();
        field.selectAll();
        field.requestFocusInWindow();
    }

    /** The node went away (an undo, a change in the text): nothing is left to rename. */
    private void modelChanged() {
        MindMap map = designer.model();
        if (map == null || map.node(key) == null) {
            cancel();
        }
    }

    private void commit() {
        if (finished) {
            return;
        }
        String text = field.getText();
        close();
        if (!text.equals(initialText)) {
            rename(text);
        }
    }

    private void cancel() {
        if (!finished) {
            close();
        }
    }

    private void close() {
        finished = true;
        designer.removeModelListener(modelListener);
        canvas.remove(field);
        if (canvas.getClientProperty(OPEN) == this) {
            canvas.putClientProperty(OPEN, null);
        }
        canvas.revalidate();
        canvas.repaint();
        canvas.requestFocusInWindow();
    }

    private void rename(String text) {
        MindMap map = designer.model();
        MapNode node = map == null ? null : map.node(key);
        if (node == null || !designer.isEditable()) {
            return;
        }
        if (node.rich() && Messages.showOkCancelDialog(designer.project(), RICH_WARNING, TITLE, "Rename", Messages.getCancelButton(),
                Messages.getWarningIcon()) != Messages.OK) {
            return;
        }
        Edit edit = MindMapEdits.rename(map, key, text, MindMapAction.now());
        designer.execute(edit.label(), edit.changes(), () -> designer.select(List.of(key)));
    }
}
