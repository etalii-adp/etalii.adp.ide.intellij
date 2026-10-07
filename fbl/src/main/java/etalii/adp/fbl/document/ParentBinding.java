package etalii.adp.fbl.document;

import java.util.List;

/**
 * The rules whose elements can be this rule's parent (FBL §5.1).
 *
 * @param slot the parent's slot the child sits in, or null
 */
public record ParentBinding(List<String> rules, String slot) {

    public ParentBinding {
        rules = List.copyOf(rules);
    }
}
