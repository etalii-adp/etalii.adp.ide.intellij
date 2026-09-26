package etalii.adp.core.diagram.edit;

import java.awt.BasicStroke;
import java.awt.Cursor;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.dnd.DragSource;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.Collection;

import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.Balloon;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.util.ui.JBUI;

import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.DiagramMapping;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.view.CanvasLayer;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;

/**
 * How the canvas shows a refused gesture (research R9, FR-018). During a drag, a refused target
 * gets the platform's "not allowed" cursor and a red outline; on release, or for a refused delete,
 * a balloon near the pointer gives the reason. {@link #preview} answers what a gesture would get
 * without changing anything, so the drag feedback gives the command's own reason.
 */
public final class RefusalFeedback implements CanvasLayer {

    /** The platform's "not allowed" drag cursor; the default cursor where there is none, such as headless. */
    public static final Cursor NOT_ALLOWED = DragSource.DefaultMoveNoDrop != null ? DragSource.DefaultMoveNoDrop : Cursor.getDefaultCursor();

    private static final String KEY = "etalii.adp.diagram.refusalFeedback";
    private static final JBColor REFUSED = new JBColor(0xDB3B4B, 0xDB5C5C);
    private static final int FADE_OUT_MS = 4_000;

    private final DiagramCanvas canvas;
    private Rectangle2D refusedArea;
    private String refusedReason;
    private String lastBalloon;

    private RefusalFeedback(DiagramCanvas canvas) {
        this.canvas = canvas;
    }

    /** Paint refusals on {@code canvas} from now on. */
    public static RefusalFeedback install(DiagramCanvas canvas) {
        RefusalFeedback feedback = new RefusalFeedback(canvas);
        canvas.putClientProperty(KEY, feedback);
        canvas.addLayer(feedback);
        return feedback;
    }

    /** The canvas's refusal feedback, or {@code null} before the editing feature is installed. */
    public static RefusalFeedback of(DiagramCanvas canvas) {
        return canvas.getClientProperty(KEY) instanceof RefusalFeedback feedback ? feedback : null;
    }

    /** Commands on the designer that check everything a gesture would and change nothing: the drag feedback's verdicts. */
    public static DiagramCommands preview(DiagramDesigner designer) {
        return new DiagramCommands(new DryRun(designer));
    }

    /** Show that dropping on {@code area}, in diagram coordinates, is refused. */
    public void showRefused(Rectangle2D area, String reason) {
        refusedArea = area == null ? null : (Rectangle2D) area.clone();
        refusedReason = reason;
        canvas.setCursor(NOT_ALLOWED);
        canvas.repaint();
    }

    /** No refusal shown any more. */
    public void clear() {
        if (refusedArea != null || refusedReason != null) {
            refusedArea = null;
            refusedReason = null;
            canvas.setCursor(null);
            canvas.repaint();
        }
    }

    /** True while a refused target is shown. */
    public boolean showingRefusal() {
        return refusedReason != null;
    }

    /** The refused target shown during a drag, in diagram coordinates, or {@code null}. */
    public Rectangle2D refusedArea() {
        return refusedArea == null ? null : (Rectangle2D) refusedArea.clone();
    }

    /** Why the target shown is refused, or {@code null}. */
    public String refusedReason() {
        return refusedReason;
    }

    /** The reason of the last balloon shown, or {@code null}. */
    public String lastBalloon() {
        return lastBalloon;
    }

    /** A balloon with the reason near a canvas point. */
    public void balloon(Point at, String reason) {
        clear();
        lastBalloon = reason;
        if (!canvas.isShowing()) {
            return;
        }
        JBPopupFactory.getInstance().createHtmlTextBalloonBuilder(StringUtil.escapeXmlEntities(reason), MessageType.WARNING, null)
                .setFadeoutTime(FADE_OUT_MS).setHideOnClickOutside(true).setHideOnKeyOutside(true).createBalloon()
                .show(new RelativePoint(canvas, at), Balloon.Position.above);
    }

    /** A balloon with the reason near a diagram point. */
    public void balloon(Point2D at, String reason) {
        balloon(canvas.toCanvas(at), reason);
    }

    @Override
    public void paint(Graphics2D g, DiagramCanvas canvas, Rectangle2D visible) {
        if (refusedArea == null) {
            return;
        }
        double zoom = canvas.zoom();
        double margin = JBUI.scale(3) / zoom;
        double arc = JBUI.scale(6) / zoom;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(REFUSED);
        g.setStroke(new BasicStroke((float) (JBUI.scale(2) / zoom)));
        g.draw(new RoundRectangle2D.Double(refusedArea.getX() - margin, refusedArea.getY() - margin, refusedArea.getWidth() + 2 * margin,
                refusedArea.getHeight() + 2 * margin, arc, arc));
    }

    /** The designer as it is, where a command changes nothing and records its refusal apart. */
    private static final class DryRun extends UserDataHolderBase implements DiagramCommands.Host {

        private final DiagramDesigner designer;

        DryRun(DiagramDesigner designer) {
            this.designer = designer;
        }

        @Override
        public DiagramDefinition definition() {
            return designer.definition();
        }

        @Override
        public DiagramMapping mapping() {
            return designer.mapping();
        }

        @Override
        public Diagram diagram() {
            return designer.diagram();
        }

        @Override
        public CharSequence text() {
            return designer.text();
        }

        @Override
        public void execute(String label, TextChanges changes, Runnable reselect) {
        }

        @Override
        public void select(Collection<?> keys) {
        }

        @Override
        public Point2D toViewport(Point2D diagramPoint) {
            return designer.toViewport(diagramPoint);
        }
    }
}
