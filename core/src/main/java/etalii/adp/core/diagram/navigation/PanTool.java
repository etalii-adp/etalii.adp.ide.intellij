package etalii.adp.core.diagram.navigation;

import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;
import java.util.List;

import javax.swing.JViewport;
import javax.swing.SwingUtilities;

import com.intellij.ui.scale.JBUIScale;

import etalii.adp.core.diagram.view.CanvasTool;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramFileEditor;

/**
 * Panning (FR-025, research R16): a middle-button drag, or a left drag while Space is held,
 * scrolls the diagram's scroll pane so the diagram follows the pointer. It claims the press
 * before selection and move, so a Space-drag over an element pans rather than moves it. With the
 * diagram's pan off it does nothing and lets every event through. The scroll bars and the plain
 * wheel keep working either way. A diagram whose view asks for background panning also pans on a
 * plain left drag that starts on empty canvas; a click there, without a drag, clears the selection.
 */
public final class PanTool implements CanvasTool {

    /** How far, before scaling, the pointer moves before a press on empty canvas is a drag rather than a click. */
    private static final int THRESHOLD = 3;

    private final DiagramFileEditor fileEditor;
    private final DiagramCanvas canvas;
    private boolean space;
    private JViewport viewport;
    private boolean background;
    private boolean moved;
    private Point pressedAt;
    private Point startPosition;
    private Cursor cursorBefore;

    public PanTool(DiagramFileEditor fileEditor) {
        this.fileEditor = fileEditor;
        this.canvas = fileEditor.canvas();
        canvas.addFocusListener(new FocusAdapter() {
            @Override
            public void focusLost(FocusEvent e) {
                // Space may be released elsewhere
                space = false;
                end();
            }
        });
    }

    private boolean enabled() {
        return fileEditor.definition().view().pan();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE && enabled()) {
            space = true;
            e.consume();
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_SPACE && space) {
            space = false;
            e.consume();
        }
    }

    @Override
    public void mousePressed(MouseEvent e) {
        boolean onBackground = !space && onBackground(e);
        if (!enabled() || !(SwingUtilities.isMiddleMouseButton(e) || space && SwingUtilities.isLeftMouseButton(e) || onBackground)) {
            return;
        }
        viewport = viewportOf(canvas);
        if (viewport == null) {
            return;
        }
        background = onBackground;
        moved = false;
        startPosition = viewport.getViewPosition();
        pressedAt = inViewport(e.getPoint(), startPosition);
        cursorBefore = canvas.isCursorSet() ? canvas.getCursor() : null;
        canvas.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
        e.consume();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (pressedAt == null) {
            return;
        }
        // the canvas moves under the pointer as it scrolls, so the pointer is measured in the viewport
        Point at = inViewport(e.getPoint(), viewport.getViewPosition());
        moved |= at.distance(pressedAt) >= JBUIScale.scale(THRESHOLD);
        scrollTo(viewport, new Point(startPosition.x - (at.x - pressedAt.x), startPosition.y - (at.y - pressedAt.y)));
        e.consume();
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (pressedAt != null) {
            if (background && !moved && !fileEditor.selection().isEmpty()) {
                // a click on empty canvas still clears the selection, as it does without background panning
                fileEditor.select(List.of());
            }
            end();
            e.consume();
        }
    }

    /** A plain left press on empty canvas, with the diagram's background panning on. */
    private boolean onBackground(MouseEvent e) {
        if (!fileEditor.definition().view().backgroundPan() || fileEditor.diagram() == null || !SwingUtilities.isLeftMouseButton(e) || e.isPopupTrigger()
                || (e.getModifiersEx() & (InputEvent.CTRL_DOWN_MASK | InputEvent.META_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK | InputEvent.ALT_DOWN_MASK)) != 0) {
            return false;
        }
        Point2D point = canvas.toDiagram(e.getPoint());
        return canvas.itemAt(point) == null && canvas.anchorAt(point) == null;
    }

    @Override
    public void mouseClicked(MouseEvent e) {
        if (enabled() && (space || SwingUtilities.isMiddleMouseButton(e))) {
            e.consume();
        }
    }

    private void end() {
        if (pressedAt == null) {
            return;
        }
        pressedAt = null;
        background = false;
        startPosition = null;
        viewport = null;
        canvas.setCursor(cursorBefore);
        cursorBefore = null;
    }

    private static Point inViewport(Point onCanvas, Point viewPosition) {
        return new Point(onCanvas.x - viewPosition.x, onCanvas.y - viewPosition.y);
    }

    static JViewport viewportOf(DiagramCanvas canvas) {
        return (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, canvas);
    }

    /** Scroll to a view position, kept inside the view. */
    static void scrollTo(JViewport viewport, Point position) {
        Dimension view = viewport.getViewSize();
        Dimension extent = viewport.getExtentSize();
        int x = Math.max(0, Math.min(position.x, view.width - extent.width));
        int y = Math.max(0, Math.min(position.y, view.height - extent.height));
        viewport.setViewPosition(new Point(x, y));
    }
}
