package etalii.adp.fbl.expression;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * The common subset of RE2, .NET, Java and ECMAScript regular expressions FBL §2.5 allows. A
 * validator rejects exactly what that section excludes: backreferences, lookaround, atomic groups,
 * possessive quantifiers, inline flags and Unicode property classes.
 */
public final class RegexSubset {

    /** The flags every subset expression is compiled with: only a line feed ends a line, for {@code .} and for {@code $}. */
    static final int FLAGS = Pattern.UNIX_LINES;

    private RegexSubset() {
    }

    /** Null when {@code expression} is in the subset; otherwise a sentence naming the construct. */
    public static String check(String expression) {
        boolean inClass = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '\\') {
                if (i + 1 >= expression.length()) {
                    return "The expression ends with a lone backslash.";
                }
                char next = expression.charAt(i + 1);
                if (next >= '1' && next <= '9') {
                    return "'\\" + next + "' is a backreference, which FBL's regular expressions do not allow.";
                }
                if (next == 'k') {
                    return "'\\k' is a named backreference, which FBL's regular expressions do not allow.";
                }
                if (next == 'p' || next == 'P') {
                    return "'\\" + next + "' is a Unicode property class, which FBL's regular expressions do not allow.";
                }
                i++;
                continue;
            }
            if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
                continue;
            }
            switch (c) {
                case '[' -> {
                    inClass = true;
                    if (i + 1 < expression.length() && expression.charAt(i + 1) == '^') {
                        i++;
                    }
                    if (i + 1 < expression.length() && expression.charAt(i + 1) == ']') {
                        i++;
                    }
                }
                case '(' -> {
                    if (i + 1 < expression.length() && expression.charAt(i + 1) == '?') {
                        String rest = expression.substring(i + 2);
                        if (rest.startsWith(":")) {
                            break;
                        }
                        if (rest.startsWith("<=") || rest.startsWith("<!")) {
                            return "Lookbehind is not allowed in FBL's regular expressions.";
                        }
                        if (rest.startsWith("<") || rest.startsWith("P<")) {
                            if (rest.startsWith("P<")) {
                                return "'(?P<name>' is not in the common subset; write '(?<name>'.";
                            }
                            break;
                        }
                        if (rest.startsWith("=") || rest.startsWith("!")) {
                            return "Lookahead is not allowed in FBL's regular expressions.";
                        }
                        if (rest.startsWith(">")) {
                            return "Atomic groups are not allowed in FBL's regular expressions.";
                        }
                        return "Inline flags are not allowed in FBL's regular expressions; use the rule's caseInsensitive.";
                    }
                }
                case '*', '+', '?', '}' -> {
                    if (i + 1 < expression.length() && expression.charAt(i + 1) == '+') {
                        return "Possessive quantifiers are not allowed in FBL's regular expressions.";
                    }
                }
                default -> {
                }
            }
        }
        if (inClass) {
            return "A character class is not closed.";
        }
        try {
            Pattern.compile(toJava(expression, false), FLAGS);
        } catch (PatternSyntaxException e) {
            return "The expression does not compile: " + e.getDescription();
        }
        return null;
    }

    /**
     * The Java form of a subset expression: {@code \d} and {@code \w} mean their ASCII classes, as in
     * RE2 and ECMAScript, and case-insensitivity covers ASCII letters only. What this engine reads
     * differently from the other engines of the subset is written so that it reads the same: a
     * bracket or an ampersand inside a class and a brace that starts no quantifier are literals,
     * {@code \v} is the vertical tab and {@code \b} inside a class the backspace. Compile the result
     * with {@link Pattern#UNIX_LINES}.
     */
    public static String toJava(String expression, boolean caseInsensitive) {
        return translate(expression, caseInsensitive).pattern();
    }

    /**
     * The Java form with its named groups.
     *
     * @param pattern   the expression to compile
     * @param names     the names of the groups as the expression spells them, in the order they first appear
     * @param javaNames for each of those names, the name the group has in {@code pattern}: this engine allows
     *                  only ASCII letters and digits in a name, so a name with an underscore is given another
     */
    record Translation(String pattern, List<String> names, Map<String, String> javaNames) {
    }

    static Translation translate(String expression, boolean caseInsensitive) {
        Map<String, String> javaNames = javaNames(groupNames(expression));
        StringBuilder builder = new StringBuilder(expression.length() + 16);
        boolean inClass = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '\\' && i + 1 < expression.length()) {
                char next = expression.charAt(i + 1);
                i++;
                switch (next) {
                    case 'd' -> builder.append(inClass ? "0-9" : "[0-9]");
                    case 'D' -> builder.append(inClass ? "\\D" : "[^0-9]");
                    case 'w' -> builder.append(inClass ? "A-Za-z0-9_" : "[A-Za-z0-9_]");
                    case 'W' -> builder.append(inClass ? "\\W" : "[^A-Za-z0-9_]");
                    // Here \v would be every vertical white space; in the subset it is the vertical tab.
                    case 'v' -> builder.append("\\x0B");
                    case 'b' -> builder.append(inClass ? "\\x08" : "\\b");
                    case '0' -> builder.append(i + 1 < expression.length() && isOctalDigit(expression.charAt(i + 1)) ? "\\0" : "\\x00");
                    default -> builder.append('\\').append(next);
                }
                continue;
            }
            if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
                if (c == '[' || c == '&') {
                    // A nested class and an intersection are this engine's own; in the subset both are literals.
                    builder.append('\\').append(c);
                } else {
                    builder.append(asciiCase(c, caseInsensitive, true));
                }
                continue;
            }
            if (c == '[') {
                inClass = true;
                builder.append(c);
                if (i + 1 < expression.length() && expression.charAt(i + 1) == '^') {
                    builder.append(expression.charAt(++i));
                }
                if (i + 1 < expression.length() && expression.charAt(i + 1) == ']') {
                    i++;
                    builder.append("\\]");
                }
                continue;
            }
            if (c == '(' && i + 2 < expression.length() && expression.charAt(i + 1) == '?' && expression.charAt(i + 2) == '<') {
                int close = expression.indexOf('>', i);
                if (close < 0) {
                    // Not a group name; the engine says what is wrong when it compiles.
                    builder.append(expression, i, expression.length());
                    break;
                }
                String name = expression.substring(i + 3, close);
                builder.append("(?<").append(javaNames.getOrDefault(name, name)).append('>');
                i = close;
                continue;
            }
            if (c == '{' && !startsQuantifier(expression, i)) {
                builder.append("\\{");
                continue;
            }
            builder.append(asciiCase(c, caseInsensitive, false));
        }
        return new Translation(builder.toString(), List.copyOf(javaNames.keySet()), javaNames);
    }

    /** The names of the named groups, in the order they first appear. */
    private static List<String> groupNames(String expression) {
        List<String> names = new ArrayList<>();
        boolean inClass = false;
        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);
            if (c == '\\' && i + 1 < expression.length()) {
                i++;
                continue;
            }
            if (inClass) {
                if (c == ']') {
                    inClass = false;
                }
                continue;
            }
            if (c == '[') {
                inClass = true;
                if (i + 1 < expression.length() && expression.charAt(i + 1) == '^') {
                    i++;
                }
                if (i + 1 < expression.length() && expression.charAt(i + 1) == ']') {
                    i++;
                }
                continue;
            }
            if (c == '(' && i + 2 < expression.length() && expression.charAt(i + 1) == '?' && expression.charAt(i + 2) == '<') {
                int close = expression.indexOf('>', i);
                if (close < 0) {
                    break;
                }
                String name = expression.substring(i + 3, close);
                if (!names.contains(name)) {
                    names.add(name);
                }
                i = close;
            }
        }
        return names;
    }

    /** Each name with the name it has in the Java form: itself where this engine allows it. */
    private static Map<String, String> javaNames(List<String> names) {
        Map<String, String> javaNames = new LinkedHashMap<>();
        int next = 0;
        for (String name : names) {
            if (isJavaName(name) || !isWordName(name)) {
                // A name that is no name at all is left for the engine to refuse.
                javaNames.put(name, name);
                continue;
            }
            String generated;
            do {
                generated = "g" + next++;
            } while (names.contains(generated));
            javaNames.put(name, generated);
        }
        return javaNames;
    }

    private static boolean isJavaName(String name) {
        if (name.isEmpty() || !isAsciiLetter(name.charAt(0))) {
            return false;
        }
        for (int i = 1; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!isAsciiLetter(c) && !(c >= '0' && c <= '9')) {
                return false;
            }
        }
        return true;
    }

    private static boolean isWordName(String name) {
        if (name.isEmpty() || (name.charAt(0) >= '0' && name.charAt(0) <= '9')) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c != '_' && !Character.isLetterOrDigit(c)) {
                return false;
            }
        }
        return true;
    }

    /** True when the brace at {@code at} opens {@code {n}}, {@code {n,}} or {@code {n,m}}; any other brace is a literal. */
    private static boolean startsQuantifier(String expression, int at) {
        int i = at + 1;
        int digits = i;
        while (i < expression.length() && expression.charAt(i) >= '0' && expression.charAt(i) <= '9') {
            i++;
        }
        if (i == digits) {
            return false;
        }
        if (i < expression.length() && expression.charAt(i) == ',') {
            i++;
            while (i < expression.length() && expression.charAt(i) >= '0' && expression.charAt(i) <= '9') {
                i++;
            }
        }
        return i < expression.length() && expression.charAt(i) == '}';
    }

    private static boolean isOctalDigit(char c) {
        return c >= '0' && c <= '7';
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static String asciiCase(char c, boolean caseInsensitive, boolean inClass) {
        if (!caseInsensitive || !isAsciiLetter(c)) {
            return String.valueOf(c);
        }
        char lower = Character.toLowerCase(c);
        char upper = Character.toUpperCase(c);
        return inClass ? "" + lower + upper : "[" + lower + upper + "]";
    }
}
