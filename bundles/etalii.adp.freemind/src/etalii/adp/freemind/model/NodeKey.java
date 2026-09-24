package etalii.adp.freemind.model;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Identifies a node across re-parses: its {@code ID}, or its index path from the root (for example
 * {@code 0/3/1}) when it has none. Exactly one of the two is set.
 */
public record NodeKey(String id, String path) {

    public NodeKey {
        if ((id == null) == (path == null)) {
            throw new IllegalArgumentException("A node key is an ID or an index path");
        }
    }

    public static NodeKey ofId(String id) {
        return new NodeKey(id, null);
    }

    public static NodeKey ofPath(int... indices) {
        return new NodeKey(null, Arrays.stream(indices).mapToObj(Integer::toString).collect(Collectors.joining("/")));
    }

    /** A path key from its written form, for example {@code 0/3/1}. */
    public static NodeKey ofPath(String path) {
        return new NodeKey(null, path);
    }

    @Override
    public String toString() {
        return id != null ? id : path;
    }
}
