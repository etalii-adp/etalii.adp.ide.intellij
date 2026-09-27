package etalii.adp.core.settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One setting a designer declares for its own page under ADP (FR-014, research R6). Values are
 * stored as text and checked against the declaration when read.
 *
 * @param min the smallest number, for {@link Kind#NUMBER}
 * @param max the largest number, for {@link Kind#NUMBER}
 * @param choices the values to choose from, for {@link Kind#CHOICE}
 */
public record DesignerSetting(String key, String label, Kind kind, String defaultValue, int min, int max, List<String> choices) {

    private static final Pattern KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]*");

    public enum Kind {
        YES_NO, NUMBER, CHOICE
    }

    public DesignerSetting {
        choices = List.copyOf(choices);
    }

    public static DesignerSetting yesNo(String key, String label, boolean defaultValue) {
        return new DesignerSetting(key, label, Kind.YES_NO, Boolean.toString(defaultValue), 0, 0, List.of());
    }

    public static DesignerSetting number(String key, String label, int defaultValue, int min, int max) {
        return new DesignerSetting(key, label, Kind.NUMBER, Integer.toString(defaultValue), min, max, List.of());
    }

    public static DesignerSetting choice(String key, String label, String defaultValue, String... choices) {
        return new DesignerSetting(key, label, Kind.CHOICE, defaultValue, 0, 0, List.of(choices));
    }

    /** True when {@code value} is a valid value of this setting. */
    public boolean accepts(String value) {
        if (value == null) {
            return false;
        }
        return switch (kind) {
        case YES_NO -> value.equals("true") || value.equals("false");
        case NUMBER -> {
            try {
                int number = Integer.parseInt(value);
                yield number >= min && number <= max;
            } catch (NumberFormatException e) {
                yield false;
            }
        }
        case CHOICE -> choices.contains(value);
        };
    }

    /** Every rule a designer's list breaks, each naming the setting; empty when it holds together. */
    public static List<String> problems(List<DesignerSetting> settings) {
        List<String> problems = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (DesignerSetting setting : settings) {
            String name = "setting '" + setting.key + "': ";
            if (setting.key == null || !KEY.matcher(setting.key).matches()) {
                problems.add(name + "the key must match " + KEY.pattern());
            } else if (!keys.add(setting.key)) {
                problems.add(name + "is declared twice");
            }
            if (setting.label == null || setting.label.isBlank()) {
                problems.add(name + "the label is empty");
            }
            if (setting.kind == Kind.NUMBER && setting.min > setting.max) {
                problems.add(name + "the range " + setting.min + " to " + setting.max + " is empty");
            } else if (setting.kind == Kind.CHOICE && setting.choices.isEmpty()) {
                problems.add(name + "declares no choices");
            } else if (!setting.accepts(setting.defaultValue)) {
                problems.add(name + (setting.kind == Kind.NUMBER ? "the default " + setting.defaultValue + " is outside " + setting.min + " to " + setting.max
                        : "the default '" + setting.defaultValue + "' is not one of the choices"));
            }
        }
        return problems;
    }
}
