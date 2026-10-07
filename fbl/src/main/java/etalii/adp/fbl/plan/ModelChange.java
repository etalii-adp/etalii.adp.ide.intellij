package etalii.adp.fbl.plan;

import java.util.Map;

/**
 * One model change, as a DISL transaction makes it and as a conformance fixture's step names it
 * (FBL §6.4, §15.3). Planning turns it into one {@link etalii.adp.fbl.Edit} or a refusal.
 */
public sealed interface ModelChange {

    /**
     * Adds an element or relation; a relation's ends are the attributes {@code source} and {@code target}, by id.
     *
     * @param id the id of the new element, or null
     * @param attributes the values by attribute name; a value may be null
     * @param parentId the id of the element the new one is placed in, or null
     */
    record Add(String type, String id, Map<String, Object> attributes, String parentId) implements ModelChange {

        public Add(String type, String id, Map<String, Object> attributes) {
            this(type, id, attributes, null);
        }
    }

    /**
     * Sets attributes of one element or relation; an empty string, an empty list or null empties an attribute.
     *
     * @param attributes the values by attribute name; a value may be null
     */
    record Set(String id, Map<String, Object> attributes) implements ModelChange {
    }

    record Remove(String id) implements ModelChange {
    }

    /** Places an element on the canvas: an edit of the registration, not of the body (FBL §8.3). */
    record Place(String id, double x, double y) implements ModelChange {
    }

    /** Stores the id of the element with a natural key in the registration's {@code identities} (FBL §8.6). */
    record Identify(String key, String id) implements ModelChange {
    }

    /** Saves without a change: no splice (FBL §15.3). */
    record Save() implements ModelChange {
    }
}
