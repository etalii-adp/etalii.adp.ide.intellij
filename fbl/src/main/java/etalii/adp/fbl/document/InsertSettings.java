package etalii.adp.fbl.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * How a rule's new entry is written (FBL §6.2). What the rule does not state is null or empty.
 *
 * @param place "after-last", "end", "start", "last-child", "next-sibling", "end-of-document" or "before"
 * @param constant the keys and values every new entry is given as they are (FBL 0.8 §6.2), in the constant's member
 *     order: a {@link String}, a {@link Long}, a {@link Double}, a {@link Boolean} or null, or {@link #NOT_A_SCALAR}
 *     for a member that is none of them, which loading refuses; null when the insert has no constant
 */
public record InsertSettings(String place, String placeBefore, String container, CreateContainer create, List<String> keys, String emit, String skeleton,
        String when, Map<String, Object> constant) {

    /** A constant's member whose value is an object or an array. */
    public static final Object NOT_A_SCALAR = new Object();

    public InsertSettings {
        keys = List.copyOf(keys);
        constant = constant == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(constant));
    }
}
