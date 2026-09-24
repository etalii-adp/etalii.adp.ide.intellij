package etalii.adp.freemind.ui;

import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.InputEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;

/**
 * Moves a node with its subtree by dragging it on the canvas (spec 001 FR-022): dropped on the top
 * quarter of another node it goes before it, on the bottom quarter after it, anywhere else onto it
 * as its last child. While dragging, a line or a box shows where it would go; a drop that cannot
 * happen (the root, into its own subtree, beside the root, a read-only file) shows nothing and
 * changes nothing. A drop is one "Move Node" step. Nodes are never positioned freely.
 */
public final class DragMove extends MouseAdapter {

    private static final String FEEDBACK = "etalii.adp.freemind.DragMove.feedback";

    private final MindMapDesigner designer;
    private final MindMapCanvas canvas;
    private NodeKey dragged;
    private Point pressedAt;
    private boolean dragging;
    private Feedback feedback;

    DragMove(MindMapDesigner designer) {
        this.designer = designer;
        this.canvas = designer.canvas();
    }

    /** Listen to the canvas. */
    void attach() {
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
    }

    /** Stop listening and remove any feedback. */
    void detach() {
        canvas.removeMouseListener(this);
        canvas.removeMouseMotionListener(this);
        reset();
    }

    /** The drop feedback shown on the canvas, or {@code null} while nothing is dragged over a target. */
    public static JComponent feedbackOf(MindMapCanvas canvas) {
        return canvas.getClientProperty(FEEDBACK) instanceof JComponent shown ? shown : null;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        reset();
        if (!SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger()
                || (e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)) != 0) {
            return;
        }
        MindMap map = designer.model();
        NodeKey key = canvas.keyAt(e.getPoint());
        if (map == null || key == null || map.node(key) == null || map.node(key) == map.root() || !designer.isEditable()) {
            return;
        }
        dragged = key;
        pressedAt = e.getPoint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (dragged == null) {
            return;
        }
        if (!dragging && pressedAt.distance(e.getPoint()) < JBUIScale.scale(3)) {
            return;
        }
        dragging = true;
        Drop drop = dropAt(e.getPoint());
        showFeedback(drop);
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        try {
            if (dragging && designer.isEditable()) {
                Drop drop = dropAt(e.getPoint());
                if (drop != null) {
                    perform(drop);
                }
            }
        } finally {
            reset();
        }
    }

    private void perform(Drop drop) {
        MindMap map = designer.model();
        MapNode node = map.node(dragged);
        MapNode target = map.node(drop.target());
        Edit edit = MindMapEdits.move(map, dragged, drop.target(), drop.placement());
        if (edit == null) {
            return;
        }
        if (drop.placement() == Placement.INTO && designer.isShownFolded(target)) {
            designer.setShownFolded(target, false);
        }
        designer.execute(edit.label(), edit.changes(), () -> designer.select(node.id() != null ? List.of(node.key()) : List.of()));
    }

    /** Where a drop at this point would go, or {@code null} when it cannot go there. */
    private Drop dropAt(Point point) {
        MindMap map = designer.model();
        NodeKey targetKey = canvas.keyAt(point);
        if (map == null || targetKey == null || targetKey.equals(dragged) || map.node(dragged) == null || map.node(targetKey) == null) {
            return null;
        }
        Rectangle box = canvas.boundsOf(targetKey);
        int band = Math.max(1, box.height / 4);
        Placement placement = point.y < box.y + band ? Placement.BEFORE : point.y >= box.y + box.height - band ? Placement.AFTER : Placement.INTO;
        MapNode node = map.node(dragged);
        MapNode target = map.node(targetKey);
        if (node.contains(target) || (placement != Placement.INTO && target == map.root())) {
            return null;
        }
        return MindMapEdits.move(map, dragged, targetKey, placement) == null ? null : new Drop(targetKey, placement, box);
    }

    private void showFeedback(Drop drop) {
        if (drop == null) {
            removeFeedback();
            return;
        }
        if (feedback == null) {
            feedback = new Feedback();
            canvas.add(feedback, 0);
            canvas.putClientProperty(FEEDBACK, feedback);
        }
        feedback.show(drop);
    }

    private void removeFeedback() {
        if (feedback != null) {
            canvas.remove(feedback);
            canvas.putClientProperty(FEEDBACK, null);
            canvas.repaint(feedback.getBounds());
            feedback = null;
        }
    }

    private void reset() {
        dragged = null;
        pressedAt = null;
        dragging = false;
        removeFeedback();
    }

    private record Drop(NodeKey target, Placement placement, Rectangle box) {
    }

    /** A line above or below the target, or a box around it, painted over the canvas. */
    private static final class Feedback extends JComponent {

        private static final JBColor COLOR = new JBColor(0x3574F0, 0x548AF7);
        private Placement placement;

        Feedback() {
            setOpaque(false);
        }

        void show(Drop drop) {
            placement = drop.placement();
            int margin = JBUIScale.scale(3);
            Rectangle box = drop.box();
            Rectangle bounds = switch (placement) {
            case BEFORE -> new Rectangle(box.x - margin, box.y - 2 * margin, box.width + 2 * margin, 2 * margin);
            case AFTER -> new Rectangle(box.x - margin, box.y + box.height, box.width + 2 * margin, 2 * margin);
            case INTO -> new Rectangle(box.x - margin, box.y - margin, box.width + 2 * margin, box.height + 2 * margin);
            };
            Rectangle old = getBounds();
            setBounds(bounds);
            if (getParent() != null) {
                getParent().repaint(old.x, old.y, old.width, old.height);
                getParent().repaint(bounds.x, bounds.y, bounds.width, bounds.height);
            }
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(COLOR);
                g.setStroke(new BasicStroke(JBUIScale.scale(2f)));
                if (placement == Placement.INTO) {
                    g.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, JBUIScale.scale(6), JBUIScale.scale(6));
                } else {
                    int y = getHeight() / 2;
                    g.drawLine(0, y, getWidth(), y);
                }
            } finally {
                g.dispose();
            }
        }
    }
}
