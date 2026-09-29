package etalii.adp.core.diagram.view;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;

import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.ViewOptions;
import etalii.adp.core.settings.AdpSettings;
import etalii.adp.core.settings.CanvasOption;

/**
 * The grid (spec 004, FR-011): a dot at every grid point, under the elements and above the
 * sectors, when showing the grid is effectively on for the diagram. Dots keep their size on the
 * screen at every zoom; when they would crowd together, only every second, fourth... point is
 * drawn.
 */
public final class GridLayer implements CanvasLayer {

    /** Readable on the canvas in the light and dark themes, quieter than any element. */
    public static final JBColor DOT = new JBColor(0xC9CCD6, 0x43454A);

    /** Closer than this on the screen, dots are thinned out. */
    private static final int MIN_GAP = 6;

    private final DiagramFileEditor tool;

    public GridLayer(DiagramFileEditor tool) {
        this.tool = tool;
    }

    @Override
    public int order() {
        return BELOW_CONTENT + 1;
    }

    @Override
    public void paint(Graphics2D g, DiagramCanvas canvas, Rectangle2D visible) {
        ViewOptions view = tool.definition().view();
        if (view.grid() <= 0 || !AdpSettings.getInstance().effective(CanvasOption.SHOW_GRID, view)) {
            return;
        }
        double zoom = canvas.zoom();
        double step = view.grid();
        while (step * zoom < JBUI.scale(MIN_GAP)) {
            step *= 2;
        }
        double size = JBUI.scale(2) / zoom;
        g.setColor(DOT);
        double startX = Math.ceil(Math.max(0, visible.getMinX()) / step) * step;
        double startY = Math.ceil(Math.max(0, visible.getMinY()) / step) * step;
        for (double y = startY; y <= visible.getMaxY(); y += step) {
            for (double x = startX; x <= visible.getMaxX(); x += step) {
                g.fill(new Rectangle2D.Double(x - size / 2, y - size / 2, size, size));
            }
        }
    }
}
