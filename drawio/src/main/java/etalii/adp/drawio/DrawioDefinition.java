package etalii.adp.drawio;

import static etalii.adp.core.diagram.EditorKind.choice;
import static etalii.adp.core.diagram.EditorKind.option;

import java.awt.geom.Path2D;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import etalii.adp.core.diagram.ArrowHead;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.DiagramRules;
import etalii.adp.core.diagram.Direction;
import etalii.adp.core.diagram.EditorKind;
import etalii.adp.core.diagram.ElementType;
import etalii.adp.core.diagram.LabelSlot;
import etalii.adp.core.diagram.LineStyle;
import etalii.adp.core.diagram.Orientation;
import etalii.adp.core.diagram.Outline;
import etalii.adp.core.diagram.Resize;
import etalii.adp.core.diagram.Sizing;
import etalii.adp.core.diagram.SlotPosition;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.xml.XmlEdits;

/**
 * The draw.io diagram (research R20, data-model.md "draw.io mapping"): the vertex shapes the spec
 * lists, draw.io lists (a stacked swimlane, such as a UML class) with their rows as parts, one
 * {@code edge} connection type whose looks are per-edge properties, and other swimlanes as
 * diagram-space sectors. Anchors sit at the quarter points of each side, where draw.io's own
 * connection points are, and on the outline; like draw.io's, they show only while connecting.
 * A row connects only on its left and right, as draw.io's {@code portConstraint=eastwest} rows do.
 */
public final class DrawioDefinition {

    public static final String EDGE = "edge";
    public static final String SWIMLANE = "swimlane";
    public static final String LIST = "list";
    public static final String ROW = "row";

    /** A vertex type: its name, outline, the style and size draw.io gives a new one, and where its label goes. */
    record Shape(String label, Outline outline, String style, int width, int height, SlotPosition text) {
    }

    /** draw.io's trapezoid: the top edge a fifth shorter at each end. */
    private static final Outline TRAPEZOID = Outline.custom(b -> {
        Path2D.Double path = new Path2D.Double();
        path.moveTo(b.getX() + b.getWidth() * 0.2, b.getY());
        path.lineTo(b.getMaxX() - b.getWidth() * 0.2, b.getY());
        path.lineTo(b.getMaxX(), b.getMaxY());
        path.lineTo(b.getX(), b.getMaxY());
        path.closePath();
        return path;
    });

    /** By type id, which is the draw.io shape or style name. */
    static final Map<String, Shape> SHAPES = new LinkedHashMap<>();

    static {
        SHAPES.put("rectangle", new Shape("Rectangle", Outline.RECTANGLE, "rounded=0", 120, 60, SlotPosition.CENTER));
        SHAPES.put("rounded", new Shape("Rounded Rectangle", Outline.ROUNDED_RECTANGLE, "rounded=1", 120, 60, SlotPosition.CENTER));
        SHAPES.put("ellipse", new Shape("Ellipse", Outline.ELLIPSE, "ellipse", 120, 80, SlotPosition.CENTER));
        SHAPES.put("rhombus", new Shape("Rhombus", Outline.DIAMOND, "rhombus", 80, 80, SlotPosition.CENTER));
        SHAPES.put("hexagon", new Shape("Hexagon", Outline.HEXAGON, "shape=hexagon", 120, 80, SlotPosition.CENTER));
        SHAPES.put("parallelogram", new Shape("Parallelogram", Outline.PARALLELOGRAM, "shape=parallelogram", 120, 60, SlotPosition.CENTER));
        SHAPES.put("cylinder3", new Shape("Cylinder", Outline.CYLINDER, "shape=cylinder3", 60, 80, SlotPosition.CENTER));
        SHAPES.put("document", new Shape("Document", Outline.DOCUMENT, "shape=document", 120, 80, SlotPosition.CENTER));
        SHAPES.put("note", new Shape("Note", Outline.NOTE, "shape=note", 80, 100, SlotPosition.CENTER));
        SHAPES.put("text", new Shape("Text", Outline.NONE, "text", 60, 30, SlotPosition.CENTER));
        SHAPES.put("trapezoid", new Shape("Trapezoid", TRAPEZOID, "shape=trapezoid", 120, 60, SlotPosition.CENTER));
        SHAPES.put("umlFrame", new Shape("Frame", Outline.RECTANGLE, "shape=umlFrame", 300, 200, SlotPosition.TOP_LEFT));
        SHAPES.put(LIST, new Shape("List", Outline.RECTANGLE, "swimlane;childLayout=stackLayout;horizontal=1;startSize=26", 160, 110, SlotPosition.TOP));
    }

    /** The quarter points of each side, as fractions of the bounds. */
    private static final double[][] QUARTER_POINTS = Stream.of(0.25, 0.5, 0.75)
            .flatMap(f -> Stream.of(new double[] { f, 0 }, new double[] { 1, f }, new double[] { f, 1 }, new double[] { 0, f })).toArray(double[][]::new);

    private static final EditorKind ARROWS = choice(option("none", "None"), option("classic", "Classic"), option("block", "Block"),
            option("open", "Open"), option("oval", "Oval"), option("diamond", "Diamond"), option("dash", "Dash"));

    public static final DiagramDefinition DEFINITION = builder().build();

    private DrawioDefinition() {
    }

    /** The anchor at a fraction of the bounds, as draw.io writes it in {@code exitX}/{@code exitY}. */
    public static String anchorId(double fx, double fy) {
        return "x" + XmlEdits.number(fx) + "y" + XmlEdits.number(fy);
    }

    public static DiagramDefinition.Builder builder() {
        DiagramDefinition.Builder builder = DiagramDefinition.builder("drawio");
        SHAPES.forEach((id, shape) -> builder.element(id, e -> vertex(e, shape, false)));
        builder.element(ROW, e -> vertex(e, new Shape("Row", Outline.NONE, "text", 160, 26, SlotPosition.LEFT), true));
        return builder.connection(EDGE, c -> c.label("Connector").line(LineStyle.ORTHOGONAL).arrows(ArrowHead.NONE, ArrowHead.FILLED)
                .label(LabelSlot.MIDDLE, "label", true).label(LabelSlot.SOURCE, "sourceLabel", false).label(LabelSlot.TARGET, "targetLabel", false)
                .property("label", p -> p.label("Label"))
                .property("sourceLabel", p -> p.label("Source label").readOnly(true))
                .property("targetLabel", p -> p.label("Target label").readOnly(true))
                .property("edgeStyle", p -> p.label("Line").editor(choice(option("straight", "Straight"), option("orthogonal", "Orthogonal"), option("curved", "Curved"))))
                .property("startArrow", p -> p.label("Start arrow").editor(ARROWS))
                .property("endArrow", p -> p.label("End arrow").editor(ARROWS))
                .property("dashed", p -> p.label("Dashed").editor(EditorKind.BOOLEAN))
                .property("strokeWidth", p -> p.label("Line width").editor(EditorKind.number(false, 0, 100)))
                .property("id", p -> p.label("Id").readOnly(true)))
                .sector(SWIMLANE, s -> s.label("Swimlane").orientation(Orientation.VERTICAL))
                .toolbox(Stream.concat(SHAPES.keySet().stream(), Stream.of(EDGE)).toArray(String[]::new))
                .view(v -> v.zoom(true).pan(true).grid(10))
                .rules(new DiagramRules() {
                    @Override
                    public Verdict canSetProperty(Diagram d, Object key, String property) {
                        var item = d.element(key) != null ? d.element(key).properties() : d.connection(key).properties();
                        return "0".equals(item.get("editable")) ? Verdict.refuse("the file marks it as not editable (editable=0)") : Verdict.allow();
                    }
                });
    }

    /** A shape, or a list's row: a part of its list that is not moved on its own and connects only on its left and right. */
    private static void vertex(ElementType.Builder e, Shape shape, boolean row) {
        e.label(shape.label()).outline(shape.outline()).sizing(Sizing.fromDiagram(10, 10)).resize(row ? Resize.NONE : Resize.BOTH).movable(!row)
                .text("label", t -> t.position(shape.text()).wrap(true).editable(true));
        if (!row) {
            e.anchor("outline", a -> a.perimeter().visible(false).accepts(EDGE, Direction.BOTH));
        }
        for (double[] at : row ? new double[][] { { 0, 0.5 }, { 1, 0.5 } } : QUARTER_POINTS) {
            e.anchor(anchorId(at[0], at[1]), a -> a.at(at[0], at[1]).visible(false).accepts(EDGE, Direction.BOTH));
        }
        e.property("label", p -> p.label("Label").editor(EditorKind.MULTILINE).defaultValue(shape.outline() == Outline.NONE ? "Text" : null))
                .property("fillColor", p -> p.label("Fill colour").editor(EditorKind.COLOR))
                .property("strokeColor", p -> p.label("Line colour").editor(EditorKind.COLOR))
                .property("fontColor", p -> p.label("Font colour").editor(EditorKind.COLOR))
                .property("dashed", p -> p.label("Dashed").editor(EditorKind.BOOLEAN))
                .property("rounded", p -> p.label("Rounded").editor(EditorKind.BOOLEAN))
                .property("id", p -> p.label("Id").readOnly(true));
    }
}
