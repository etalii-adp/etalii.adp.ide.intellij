package etalii.adp.fbl;

import java.util.Map;

/** What a DISL id strategy is asked when a rule stores no id (FBL §5.3, DISL §11.5). {@code source} and {@code target} are null for an element. */
public record IdRequest(String rule, String type, Map<String, Object> attributes, String source, String target, int line) {
}
