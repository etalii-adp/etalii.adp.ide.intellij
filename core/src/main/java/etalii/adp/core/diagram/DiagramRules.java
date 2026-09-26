package etalii.adp.core.diagram;

import java.util.Set;

import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.End;

/**
 * A designer's own rules (FR-003), asked after the definition's permissions and before the
 * mapping. Everything is allowed unless a method says otherwise.
 */
public interface DiagramRules {

    /** @param targetOrSector the element dropped onto, else the sector at the drop point, else {@code null} */
    default Verdict canAdd(Diagram d, String type, Object targetOrSector) {
        return Verdict.allow();
    }

    /** @param keys every element and connection that would go, connections of removed elements included */
    default Verdict canRemove(Diagram d, Set<Object> keys) {
        return Verdict.allow();
    }

    default Verdict canConnect(Diagram d, String type, End source, End target) {
        return Verdict.allow();
    }

    default Verdict canDisconnect(Diagram d, Object connection) {
        return Verdict.allow();
    }

    default Verdict canDrop(Diagram d, Set<Object> keys, Object target, Placement placement) {
        return Verdict.allow();
    }

    /**
     * Whether one item's property may be set, beyond its declaration: for values that are read-only
     * for some items only (FreeMind's formatted text). A refused property shows read-only in the
     * panel and is not edited in place.
     *
     * @param key an element or connection
     */
    default Verdict canSetProperty(Diagram d, Object key, String property) {
        return Verdict.allow();
    }
}