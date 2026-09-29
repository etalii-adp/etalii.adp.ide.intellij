package etalii.adp.core.diagram.navigation;

import java.awt.BasicStroke;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Rectangle2D;

import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.diagram.Orientation;
import etalii.adp.core.diagram.SectorDecl;
import etalii.adp.core.diagram.Space;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.Sector;
import etalii.adp.core.diagram.view.CanvasLayer;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramFileEditor;
import etalii.adp.core.diagram.view.ElementPainter;

/**
 * Swimlanes and other sectors (FR-026, research R16), one layer per space. Diagram-space sectors
 * are painted under the content and the zoom, as filled bands; view-space sectors are painted in
 * viewport pixels after the content, as outlines only, so they stay fixed while the diagram
 * scrolls and zooms and never hide what lies under them. Each has a header strip with its label:
 * on the left of horizontal bands, with the label turned, and along the top of vertical ones.
 * A sector whose kind is not declared is drawn as a horizontal diagram-space band, as
 * {@code DiagramCommands} treats it.
 */
public final class SectorLayer implements CanvasLayer {

    /** The header strip's fill. */
    public static final JBColor HEADER = new JBColor(0xEBECF0, 0x2B2D30);

    static final JBColor BODY = new JBColor(0xF7F8FA, 0x232427);
    static final JBColor BORDER = new JBColor(0xC9CCD6, 0x43454A);

    /** The header strip's depth: unscaled diagram pixels in diagram space, scaled pixels in view space. */
    private static final int HEADER_SIZE = 24;

    private final DiagramFileEditor fileEditor;
    private final Space space;

    public SectorLayer(DiagramFileEditor fileEditor, Space space) {
        this.fileEditor = fileEditor;
        this.space = space;
    }

    @Override
    public Space space() {
        return space;
    }

    @Override
    public int order() {
        return BELOW_CONTENT;
    }

    /** Where a sector is drawn, in the canvas's coordinates at the current zoom and scroll, or {@code null} when there is no such sector. */
    public static Rectangle onCanvas(DiagramFileEditor fileEditor, Object sectorKey) {
        Diagram diagram = fileEditor.diagram();
        Sector sector = diagram == null ? null : diagram.sector(sectorKey);
        if (sector == null) {
            return null;
        }
        DiagramCanvas canvas = fileEditor.canvas();
        if (spaceOf(decl(fileEditor, sector)) == Space.VIEW) {
            Rectangle area = sector.bounds().getBounds();
            Point origin = canvas.viewportPosition();
            area.translate(origin.x, origin.y);
            return area;
        }
        return canvas.toCanvas(sector.bounds());
    }

    @Override
    public void paint(Graphics2D g, DiagramCanvas canvas, Rectangle2D visible) {
        Diagram diagram = fileEditor.diagram();
        if (diagram == null || diagram.sectors().isEmpty()) {
            return;
        }
        boolean view = space == Space.VIEW;
        double depth = view ? JBUI.scale(HEADER_SIZE) : HEADER_SIZE;
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setStroke(new BasicStroke(view ? JBUIScale.scale(1f) : 1f));
        Font font = JBFont.label();
        for (Sector sector : diagram.sectors().values()) {
            SectorDecl decl = decl(fileEditor, sector);
            if (spaceOf(decl) != space) {
                continue;
            }
            Rectangle2D box = sector.bounds();
            if (!box.intersects(visible)) {
                continue;
            }
            boolean horizontal = decl == null || decl.orientation() == Orientation.HORIZONTAL;
            Rectangle2D header = horizontal ? new Rectangle2D.Double(box.getX(), box.getY(), Math.min(depth, box.getWidth()), box.getHeight())
                    : new Rectangle2D.Double(box.getX(), box.getY(), box.getWidth(), Math.min(depth, box.getHeight()));
            if (!view) {
                g.setColor(BODY);
                g.fill(box);
            }
            g.setColor(HEADER);
            g.fill(header);
            g.setColor(BORDER);
            g.draw(box);
            g.draw(header);
            label(g, font, sector.label(), header, horizontal);
        }
    }

    // simplified: the label is centred on the whole header strip, so on a band longer than the window it may be off screen;
    // if that binds, centre it on the strip's visible part and repaint the whole strip on scroll

    /** The label centred in the header strip, turned to read upwards on horizontal bands, cut off at the strip. */
    private static void label(Graphics2D g, Font font, String text, Rectangle2D header, boolean horizontal) {
        if (text == null || text.isEmpty()) {
            return;
        }
        Graphics2D l = (Graphics2D) g.create();
        try {
            l.clip(header);
            l.setFont(font);
            l.setColor(ElementPainter.TEXT);
            FontMetrics metrics = l.getFontMetrics();
            l.translate(header.getCenterX(), header.getCenterY());
            if (horizontal) {
                l.rotate(-Math.PI / 2);
            }
            l.drawString(text, (float) (-metrics.stringWidth(text) / 2.0), (float) ((metrics.getAscent() - metrics.getDescent()) / 2.0));
        } finally {
            l.dispose();
        }
    }

    private static SectorDecl decl(DiagramFileEditor fileEditor, Sector sector) {
        return fileEditor.definition().sectors().get(sector.declId());
    }

    private static Space spaceOf(SectorDecl decl) {
        return decl == null ? Space.DIAGRAM : decl.space();
    }
}
