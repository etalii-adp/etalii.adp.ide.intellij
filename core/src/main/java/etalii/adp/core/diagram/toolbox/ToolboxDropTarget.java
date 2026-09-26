package etalii.adp.core.diagram.toolbox;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Objects;

import javax.swing.JComponent;

import com.intellij.ide.dnd.DnDEvent;
import com.intellij.ide.dnd.DnDManager;
import com.intellij.ide.dnd.DnDTarget;
import com.intellij.openapi.Disposable;

import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.Sizing;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.edit.MoveTool;
import etalii.adp.core.diagram.edit.RefusalFeedback;
import etalii.adp.core.diagram.toolbox.ToolboxDragSource.ToolboxDrag;
import etalii.adp.core.diagram.view.DiagramCanvas;
import etalii.adp.core.diagram.view.DiagramDesigner;
import etalii.adp.core.diagram.view.Scene.ElementRender;

/**
 * Takes toolbox drags on a diagram canvas, through the platform's {@code DnDManager}. The new
 * element's top left lands at the drop point, snapped to the grid; dropped on an element, a type
 * that may be dropped onto others goes onto it. A refused drop shows the refusal while dragging
 * and its reason in a balloon when released, and changes nothing.
 */
public final class ToolboxDropTarget implements DnDTarget {

    private static final String KEY = "etalii.adp.toolbox.dropTarget";

    private final DiagramDesigner designer;
    private final DiagramCanvas canvas;
    private final RefusalFeedback feedback;
    private Object previewedFor;
    private Verdict previewed;

    private ToolboxDropTarget(DiagramDesigner designer, RefusalFeedback feedback) {
        this.designer = designer;
        this.canvas = designer.canvas();
        this.feedback = feedback;
    }

    /** Take toolbox drops on the designer's canvas until {@code lifetime} ends. */
    public static void install(DiagramDesigner designer, RefusalFeedback feedback, Disposable lifetime) {
        ToolboxDropTarget target = new ToolboxDropTarget(designer, feedback);
        designer.canvas().putClientProperty(KEY, target);
        DnDManager.getInstance().registerTarget(target, designer.canvas(), lifetime);
    }

    /** The toolbox drop target of a canvas, or {@code null}. */
    public static ToolboxDropTarget of(JComponent component) {
        return component.getClientProperty(KEY) instanceof ToolboxDropTarget target ? target : null;
    }

    /** Enter on an element entry: the element centred in the visible canvas, as far as its declared size says. */
    static void addAtCentre(DiagramDesigner designer, String typeId) {
        ElementType type = designer.definition().elementType(typeId);
        if (type == null || designer.diagram() == null) {
            return;
        }
        Rectangle2D visible = designer.canvas().visibleArea();
        double width = switch (type.sizing()) {
        case Sizing.Fixed fixed -> fixed.width();
        case Sizing.FromDiagram from -> from.minWidth();
        case Sizing.Auto auto -> 0;
        };
        double height = switch (type.sizing()) {
        case Sizing.Fixed fixed -> fixed.height();
        case Sizing.FromDiagram from -> from.minHeight();
        case Sizing.Auto auto -> 0;
        };
        Point2D centre = new Point2D.Double(visible.getCenterX(), visible.getCenterY());
        Point2D at = new Point2D.Double(MoveTool.snap(designer, centre.getX() - width / 2), MoveTool.snap(designer, centre.getY() - height / 2));
        Verdict verdict = designer.commands().add(typeId, at, null);
        RefusalFeedback feedback = RefusalFeedback.of(designer.canvas());
        if (!verdict.allowed() && feedback != null) {
            feedback.balloon(centre, verdict.reason());
        }
    }

    /** A drag of the element type released at a canvas point: added there, or refused with a balloon. */
    public Verdict dropAt(String typeId, Point point) {
        feedback.clear();
        previewedFor = null;
        Verdict verdict = designer.commands().add(typeId, snapped(point), targetAt(typeId, point));
        if (!verdict.allowed()) {
            feedback.balloon(point, verdict.reason());
        } else {
            canvas.requestFocusInWindow();
        }
        return verdict;
    }

    @Override
    public boolean update(DnDEvent event) {
        if (!(event.getAttachedObject() instanceof ToolboxDrag drag) || designer.definition().elementType(drag.typeId()) == null) {
            event.setDropPossible(false);
            return false;
        }
        Point point = event.getPointOn(canvas);
        Object target = targetAt(drag.typeId(), point);
        Object key = List.of(drag.typeId(), Objects.requireNonNullElse(target, ""));
        if (!key.equals(previewedFor)) {
            previewedFor = key;
            previewed = RefusalFeedback.preview(designer).add(drag.typeId(), snapped(point), target);
        }
        if (previewed.allowed()) {
            feedback.clear();
            event.setDropPossible(true);
        } else {
            ElementRender render = target == null ? null : canvas.scene().elements().get(target);
            feedback.showRefused(render != null ? render.bounds() : new Rectangle2D.Double(canvas.toDiagram(point).getX(), canvas.toDiagram(point).getY(), 0, 0),
                    previewed.reason());
            event.setDropPossible(false, previewed.reason());
        }
        return false;
    }

    @Override
    public void drop(DnDEvent event) {
        if (event.getAttachedObject() instanceof ToolboxDrag drag) {
            dropAt(drag.typeId(), event.getPointOn(canvas));
        }
    }

    @Override
    public void cleanUpOnLeave() {
        feedback.clear();
        previewedFor = null;
    }

    private Point2D snapped(Point point) {
        Point2D at = canvas.toDiagram(point);
        return new Point2D.Double(MoveTool.snap(designer, at.getX()), MoveTool.snap(designer, at.getY()));
    }

    /** The element a drop at a canvas point goes onto: the topmost one there, when the type may be dropped onto others. */
    private Object targetAt(String typeId, Point point) {
        ElementType type = designer.definition().elementType(typeId);
        return type != null && type.droppableOnto() ? canvas.elementAt(canvas.toDiagram(point)) : null;
    }
}
