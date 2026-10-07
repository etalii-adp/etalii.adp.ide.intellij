package etalii.adp.fbl;

import java.util.Map;

import etalii.adp.fbl.text.Span;

/**
 * An element or a relation read from a body (FBL §5). {@code idIsStored} is false when the id is not
 * kept in the body: derived by the caller's DISL id strategy, or a place-based address. The
 * attributes are in the order the rule binds them, and a value may be null. {@code parentId},
 * {@code parentSlot}, {@code source} and {@code target} are null where they do not apply.
 */
public record FblElement(
        String id,
        boolean idIsStored,
        String type,
        String rule,
        boolean isRelation,
        Map<String, Object> attributes,
        String parentId,
        String parentSlot,
        String source,
        String target,
        Span ownSpan,
        int line) {
}
