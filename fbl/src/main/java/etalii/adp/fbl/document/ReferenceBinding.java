package etalii.adp.fbl.document;

import java.util.List;

/** A value that names another element (FBL §5.4): the rules it may name, and by which attribute. */
public record ReferenceBinding(List<String> to, String by) {

    public ReferenceBinding {
        to = List.copyOf(to);
    }
}
