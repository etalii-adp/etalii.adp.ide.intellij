package etalii.adp.fbl.document;

import java.util.List;

/**
 * How a rule's new entry is written (FBL §6.2). What the rule does not state is null or empty.
 *
 * @param place "after-last", "end", "start", "last-child", "next-sibling", "end-of-document" or "before"
 */
public record InsertSettings(String place, String placeBefore, String container, CreateContainer create, List<String> keys, String emit, String skeleton,
        String when) {

    public InsertSettings {
        keys = List.copyOf(keys);
    }
}
