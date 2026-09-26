package etalii.adp.core.diagram.view;

import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;

/**
 * A gesture on the {@link DiagramCanvas}: selection, move, connect, pan. The canvas offers each
 * event to its tools in the order they were added until one consumes it. Mouse positions are in
 * the canvas's (zoomed) coordinates; {@link DiagramCanvas#toDiagram} converts them.
 */
public interface CanvasTool {

    default void mousePressed(MouseEvent e) {
    }

    default void mouseReleased(MouseEvent e) {
    }

    default void mouseClicked(MouseEvent e) {
    }

    default void mouseDragged(MouseEvent e) {
    }

    default void mouseMoved(MouseEvent e) {
    }

    default void mouseExited(MouseEvent e) {
    }

    default void mouseWheelMoved(MouseWheelEvent e) {
    }

    default void keyPressed(KeyEvent e) {
    }

    default void keyReleased(KeyEvent e) {
    }

    default void keyTyped(KeyEvent e) {
    }
}
