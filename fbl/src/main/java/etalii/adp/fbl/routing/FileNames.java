package etalii.adp.fbl.routing;

import java.io.File;
import java.util.Objects;

/**
 * The parts of a file name that claims, templates and sidecars are matched on and named from: the
 * name after the last separator, its extension from the last {@code .}, and the name without it.
 */
public final class FileNames {

    private FileNames() {
    }

    /** The part of a path after its last separator: {@code /} everywhere, and {@code \} and {@code :} where the platform separates by them. */
    public static String name(String path) {
        Objects.requireNonNull(path, "path");
        int last = path.lastIndexOf('/');
        if (File.separatorChar == '\\') {
            last = Math.max(last, Math.max(path.lastIndexOf('\\'), path.lastIndexOf(':')));
        }
        return path.substring(last + 1);
    }

    /** A file name's extension with its {@code .}, or the empty string when it has none or ends with the {@code .}. */
    public static String extension(String path) {
        String name = name(path);
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot);
    }

    /** A file name without its last {@code .} and what follows it. */
    public static String baseName(String path) {
        String name = name(path);
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }
}
