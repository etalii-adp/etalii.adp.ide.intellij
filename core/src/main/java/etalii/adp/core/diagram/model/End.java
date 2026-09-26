package etalii.adp.core.diagram.model;

/** One end of a connection: an element and the anchor it attaches to, {@code null} for the perimeter. */
public record End(Object elementKey, String anchorId) {
}