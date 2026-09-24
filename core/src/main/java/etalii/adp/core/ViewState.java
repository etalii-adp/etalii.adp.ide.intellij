package etalii.adp.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * What one editor shows, never written to the file: display-only fold overrides, the selection and
 * the zoom. Keys are the format's own (for FreeMind, {@code NodeKey}), compared by value, so they
 * survive a re-parse.
 */
public final class ViewState {

    /** The zoom levels Zoom In and Zoom Out step through; 1.0 is Actual Size. */
    public static final double[] ZOOM_LEVELS = { 0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 3.0, 4.0 };

    private final Map<Object, Boolean> foldOverrides = new HashMap<>();
    private final List<Runnable> selectionListeners = new CopyOnWriteArrayList<>();
    private List<Object> selection = List.of();
    private double zoom = 1.0;

    /** {@code TRUE} when shown folded, {@code FALSE} when shown unfolded, {@code null} when the file decides. */
    public Boolean foldOverride(Object key) {
        return foldOverrides.get(key);
    }

    /** Fold or unfold for display only, without an edit; {@code null} lets the file decide again. */
    public void setFoldOverride(Object key, Boolean folded) {
        if (folded == null) {
            foldOverrides.remove(key);
        } else {
            foldOverrides.put(key, folded);
        }
    }

    public List<Object> selection() {
        return selection;
    }

    /** Replace the selection, in order, and tell the listeners when it changed. */
    public void select(Collection<?> keys) {
        List<Object> next = List.copyOf(new ArrayList<>(keys));
        if (!next.equals(selection)) {
            selection = next;
            selectionListeners.forEach(Runnable::run);
        }
    }

    public void addSelectionListener(Runnable listener) {
        selectionListeners.add(listener);
    }

    public void removeSelectionListener(Runnable listener) {
        selectionListeners.remove(listener);
    }

    public double zoom() {
        return zoom;
    }

    /** Clamped to the first and last of {@link #ZOOM_LEVELS}. */
    public void setZoom(double zoom) {
        this.zoom = Math.max(ZOOM_LEVELS[0], Math.min(ZOOM_LEVELS[ZOOM_LEVELS.length - 1], zoom));
    }

    public void zoomIn() {
        for (double level : ZOOM_LEVELS) {
            if (level > zoom + 1e-9) {
                zoom = level;
                return;
            }
        }
    }

    public void zoomOut() {
        for (int i = ZOOM_LEVELS.length - 1; i >= 0; i--) {
            if (ZOOM_LEVELS[i] < zoom - 1e-9) {
                zoom = ZOOM_LEVELS[i];
                return;
            }
        }
    }
}
