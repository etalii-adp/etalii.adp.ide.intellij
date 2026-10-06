package etalii.adp.fbl.routing;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * The globs of FBL §10.1 and §12.1: relative, {@code /}-separated; {@code *} within one path segment,
 * {@code **} any number of segments, {@code ?} one character, {@code […]} a character class.
 */
public final class Glob {

    private Glob() {
    }

    /** Whether the file system ignores case by the platform's convention: Windows and macOS do. */
    public static boolean platformIgnoresCase() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        return os.contains("win") || os.contains("mac") || os.contains("darwin");
    }

    public static boolean isMatch(String glob, String path, boolean ignoreCase) {
        Objects.requireNonNull(glob, "glob");
        Objects.requireNonNull(path, "path");
        int flags = ignoreCase ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        return Pattern.compile(toRegex(glob), flags).matcher(path.replace('\\', '/')).matches();
    }

    public static String toRegex(String glob) {
        StringBuilder builder = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                case '*' -> {
                    if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                        boolean slash = i + 2 < glob.length() && glob.charAt(i + 2) == '/';
                        builder.append(slash ? "(?:.*/)?" : ".*");
                        i += slash ? 2 : 1;
                    } else {
                        builder.append("[^/]*");
                    }
                }
                case '?' -> builder.append("[^/]");
                case '[' -> {
                    int close = glob.indexOf(']', i + 2);
                    if (close < 0) {
                        builder.append("\\[");
                    } else {
                        String set = glob.substring(i + 1, close);
                        if (set.startsWith("!")) {
                            set = "^" + set.substring(1);
                        }
                        // A '[' inside a class opens a nested class here and is a plain character in a glob.
                        builder.append('[').append(set.replace("\\", "\\\\").replace("[", "\\[")).append(']');
                        i = close;
                    }
                }
                default -> builder.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return builder.append('$').toString();
    }
}
