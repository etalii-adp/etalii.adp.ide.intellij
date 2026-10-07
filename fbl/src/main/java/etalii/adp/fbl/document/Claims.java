package etalii.adp.fbl.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a binding claims (FBL §12).
 *
 * @param marker the content test that decides between candidates, or null
 * @param readings per origin: whether a bare file opens as it, and what suggests it
 */
public record Claims(
        List<String> extensions,
        List<String> names,
        boolean shared,
        boolean registrationOnly,
        Marker marker,
        List<String> suggest,
        List<String> origins,
        Map<String, ReadingClaim> readings) {

    public Claims {
        extensions = List.copyOf(extensions);
        names = List.copyOf(names);
        suggest = List.copyOf(suggest);
        origins = List.copyOf(origins);
        readings = Collections.unmodifiableMap(new LinkedHashMap<>(readings));
    }
}
