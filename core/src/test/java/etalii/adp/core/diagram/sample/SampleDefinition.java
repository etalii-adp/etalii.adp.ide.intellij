package etalii.adp.core.diagram.sample;

import static etalii.adp.core.diagram.EditorKind.option;

import etalii.adp.core.diagram.ArrowHead;
import etalii.adp.core.diagram.Dash;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.Direction;
import etalii.adp.core.diagram.EditorKind;
import etalii.adp.core.diagram.LabelSlot;
import etalii.adp.core.diagram.LineStyle;
import etalii.adp.core.diagram.Orientation;
import etalii.adp.core.diagram.Outline;
import etalii.adp.core.diagram.Resize;
import etalii.adp.core.diagram.Sizing;
import etalii.adp.core.diagram.SlotPosition;
import etalii.adp.core.diagram.Space;
import etalii.adp.core.diagram.TextSlot;
import etalii.adp.core.diagram.Tone;

/**
 * The test-only sample designer (data-model.md): two element types, two connection types, lanes
 * in diagram space, a legend in view space, and every editor kind, so each framework capability
 * is exercised in isolation.
 */
public final class SampleDefinition {

    public static final DiagramDefinition DEFINITION = builder().build();

    private SampleDefinition() {
    }

    /** The sample declarations, for tests that change one part before building. */
    public static DiagramDefinition.Builder builder() {
        return DiagramDefinition.builder("sample")
                .element("task", e -> e.label("Task").outline(Outline.ROUNDED_RECTANGLE).tone(Tone.BLUE)
                        .text("title", t -> t.property("title").position(SlotPosition.CENTER).wrap(true).editable(true))
                        .text("owner", t -> t.property("owner").position(SlotPosition.BELOW).style(TextSlot.Style.SMALL).editable(true))
                        .sizing(Sizing.auto(200))
                        .resize(Resize.HORIZONTAL)
                        .anchor("in", a -> a.at(0, 0.5).accepts("flow", Direction.IN))
                        .anchor("out", a -> a.at(1, 0.5).accepts("flow", Direction.OUT))
                        .anchor("top", a -> a.at(0.5, 0).accepts("note", Direction.BOTH))
                        .anchor("bottom", a -> a.at(0.5, 1).accepts("note", Direction.BOTH))
                        .property("title", p -> p.label("Title").editor(EditorKind.MULTILINE).defaultValue("Task"))
                        .property("owner", p -> p.label("Owner"))
                        .property("priority", p -> p.label("Priority")
                                .editor(EditorKind.choice(option("low", "Low"), option("medium", "Medium"), option("high", "High"))))
                        .property("done", p -> p.label("Done").editor(EditorKind.BOOLEAN))
                        .property("estimate", p -> p.label("Estimate").category("Planning").editor(EditorKind.number(true, 0, 100)))
                        .property("color", p -> p.label("Colour").editor(EditorKind.COLOR))
                        .property("id", p -> p.label("Id").readOnly(true)))
                .element("decision", e -> e.label("Decision").outline(Outline.DIAMOND).tone(Tone.YELLOW)
                        .text("title", t -> t.property("title").wrap(true).editable(true))
                        .sizing(Sizing.fixed(100, 60))
                        .anchor("in", a -> a.at(0, 0.5).visible(false).accepts("flow", Direction.IN))
                        .anchor("out", a -> a.at(1, 0.5).visible(false).accepts("flow", Direction.OUT))
                        .anchor("top", a -> a.at(0.5, 0).visible(false).accepts("note", Direction.BOTH))
                        .property("title", p -> p.label("Title").defaultValue("?"))
                        .property("id", p -> p.label("Id").readOnly(true)))
                .connection("flow", c -> c.label("Flow")
                        .line(LineStyle.ORTHOGONAL).dash(Dash.SOLID).thickness(1.5f).tone(Tone.NEUTRAL)
                        .arrows(ArrowHead.NONE, ArrowHead.OPEN)
                        .label(LabelSlot.MIDDLE, "label", true)
                        .label(LabelSlot.SOURCE, "tag", false)
                        .label(LabelSlot.TARGET, "note", false)
                        .routed(true)
                        .property("label", p -> p.label("Label"))
                        .property("tag", p -> p.label("Tag").readOnly(true))
                        .property("note", p -> p.label("Note").readOnly(true))
                        .property("id", p -> p.label("Id").readOnly(true)))
                .connection("note", c -> c.label("Note")
                        .line(LineStyle.CURVED).dash(Dash.DASHED).tone(Tone.GREY)
                        .arrows(ArrowHead.NONE, ArrowHead.OPEN)
                        .label(LabelSlot.MIDDLE, "label", true)
                        .property("label", p -> p.label("Label"))
                        .property("id", p -> p.label("Id").readOnly(true)))
                .sector("lane", s -> s.label("Lane").orientation(Orientation.HORIZONTAL).space(Space.DIAGRAM))
                .sector("legend", s -> s.label("Legend").orientation(Orientation.VERTICAL).space(Space.VIEW))
                .toolbox("task", "decision", "flow", "note")
                .view(v -> v.zoom(true).pan(true).grid(10))
                .rules(new SampleRules());
    }
}