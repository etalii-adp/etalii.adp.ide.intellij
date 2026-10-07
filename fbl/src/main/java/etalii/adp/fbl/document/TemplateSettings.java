package etalii.adp.fbl.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** The text a new body is produced from (FBL §13), and the text for an origin that has its own. */
public record TemplateSettings(String text, Map<String, String> byOrigin) {

    public TemplateSettings {
        byOrigin = Collections.unmodifiableMap(new LinkedHashMap<>(byOrigin));
    }
}
