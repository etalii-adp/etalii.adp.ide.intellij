package etalii.adp.fbl.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An element rule or a relation rule (FBL §5.1, §5.4). What a rule does not state is null, false or
 * empty.
 *
 * @param within the rules or blocks an entry must lie within, or null when the rule does not say
 * @param attributes the attribute bindings in the order the binding writes them
 * @param readOnly null when the rule's entries may be written; the reason (possibly empty) when not
 */
public record Rule(
        String name,
        String type,
        boolean isRelation,
        String at,
        String line,
        List<String> within,
        boolean opens,
        boolean caseInsensitive,
        List<String> files,
        String when,
        IdBinding id,
        ParentBinding parent,
        Map<String, AttributeBinding> attributes,
        AttributeBinding source,
        AttributeBinding target,
        InsertSettings insert,
        RemoveSettings remove,
        boolean snapshotUndo,
        String readOnly) {

    public Rule {
        within = within == null ? null : List.copyOf(within);
        files = List.copyOf(files);
        attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
    }

    /** The binding of the attribute of that name, or null. */
    public AttributeBinding attribute(String attributeName) {
        return attributes.get(attributeName);
    }
}
