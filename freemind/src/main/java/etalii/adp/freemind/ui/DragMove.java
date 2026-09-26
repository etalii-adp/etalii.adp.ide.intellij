package etalii.adp.freemind.ui;

import javax.swing.JComponent;

/**
 * Moving a node by dragging it (spec 001 FR-022) is the framework's move tool now: it drops the
 * node before, onto or after another, as one "Move Node" step through {@link FreeMindMapping}
 * (research R19). What is left here is what spec 001's tests ask of the old gesture.
 */
public final class DragMove {

    private DragMove() {
    }

    /**
     * The drop feedback shown on the canvas as a component, or {@code null}. The framework paints
     * its feedback as a canvas layer while dragging, so there is never one.
     */
    public static JComponent feedbackOf(MindMapCanvas canvas) {
        return null;
    }
}
