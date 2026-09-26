package etalii.adp.core.diagram;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Which editor the property panel uses for a value, and which input fits it (FR-023). Values are
 * strings in the file's own notation; an empty value means "not set" and always fits.
 */
public sealed interface EditorKind {

    EditorKind TEXT = new Text();
    EditorKind MULTILINE = new Multiline();
    EditorKind BOOLEAN = new YesNo();
    EditorKind COLOR = new Colour();

    /** The reason {@code value} does not fit this editor, or {@code null} when it does. */
    String validate(String value);

    static EditorKind number(boolean integer, double min, double max) {
        return new Number(integer, min, max);
    }

    static EditorKind choice(Option... options) {
        return new Choice(List.of(options));
    }

    static Option option(String value, String label) {
        return new Option(value, label);
    }

    record Text() implements EditorKind {
        @Override
        public String validate(String value) {
            return null;
        }
    }

    record Multiline() implements EditorKind {
        @Override
        public String validate(String value) {
            return null;
        }
    }

    /** {@code true} or {@code false}. */
    record YesNo() implements EditorKind {
        @Override
        public String validate(String value) {
            return value.isEmpty() || value.equals("true") || value.equals("false") ? null : "'" + value + "' is not true or false";
        }
    }

    /** {@code #RRGGBB}. */
    record Colour() implements EditorKind {
        @Override
        public String validate(String value) {
            return value.isEmpty() || value.matches("#[0-9A-Fa-f]{6}") ? null : "'" + value + "' is not a colour like #RRGGBB";
        }
    }

    /** A decimal literal, whole when {@code integer}, within {@code min} and {@code max}. */
    record Number(boolean integer, double min, double max) implements EditorKind {
        @Override
        public String validate(String value) {
            if (value.isEmpty()) {
                return null;
            }
            double parsed;
            try {
                parsed = integer ? Long.parseLong(value.trim()) : Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                return "'" + value + "' is not " + (integer ? "a whole number" : "a number");
            }
            if (Double.isNaN(parsed) || parsed < min || parsed > max) {
                return "'" + value + "' is not between " + format(min) + " and " + format(max);
            }
            return null;
        }

        private static String format(double number) {
            return number == Math.rint(number) && Math.abs(number) < 1e15 ? Long.toString((long) number) : Double.toString(number);
        }
    }

    /** One of the declared values. */
    record Choice(List<Option> options) implements EditorKind {

        public Choice {
            options = List.copyOf(options);
        }

        @Override
        public String validate(String value) {
            return value.isEmpty() || options.stream().anyMatch(option -> option.value().equals(value)) ? null
                    : "'" + value + "' is not one of " + options.stream().map(Option::value).collect(Collectors.joining(", "));
        }
    }

    /** One value of a choice and its label in the panel. */
    record Option(String value, String label) {
    }
}