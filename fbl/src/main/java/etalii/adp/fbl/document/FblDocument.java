package etalii.adp.fbl.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A loaded FBL document (FBL §2.1): its version and its bindings by name, in document order.
 *
 * @param path the file the document was loaded from, or null when it was loaded from bytes alone
 */
public record FblDocument(String version, String path, Map<String, FblBinding> bindings) {

    public FblDocument {
        bindings = Collections.unmodifiableMap(new LinkedHashMap<>(bindings));
    }
}
