package etalii.adp.fbl.plan;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etalii.adp.fbl.document.AttributeBinding;

/**
 * The rules of FBL §6.3 that are the same for every family: numbers, times, maps and the
 * {@code emit} templates of new entries.
 */
public final class NewText {

    /** White space as Unicode counts it: the separators and the white space controls. */
    private static final String WHITE_SPACE = "[\\p{Z}\\t-\\r\\x85]";

    private static final Pattern TRIMMED = Pattern.compile(WHITE_SPACE + "*(.*?)" + WHITE_SPACE + "*", Pattern.DOTALL);

    private NewText() {
    }

    /**
     * A number as FBL §6.3 writes it: with {@code shortest} (a null {@code decimals}), an integer
     * without a fraction and any other number in ECMAScript's {@code Number.prototype.toString}
     * form; with {@code {decimals: n}}, rounded halves away from zero, trailing zeros and a
     * trailing point dropped.
     */
    public static String number(double value, Integer decimals) {
        return NumberText.number(value, decimals);
    }

    /** The value as a double when it is a number, else null. */
    public static Double tryNumber(Object value) {
        return switch (value) {
            case Long l -> (double) l;
            case Integer i -> (double) i;
            case Double d -> d;
            case BigDecimal m -> m.doubleValue();
            case null, default -> null;
        };
    }

    /**
     * A value's written form for families without typed scalars: numbers by the rule above, booleans in lower case.
     *
     * @param value the model value, or null
     * @param binding the attribute's binding, or null
     */
    public static String plain(Object value, AttributeBinding binding) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        Double n = tryNumber(value);
        if (n != null) {
            return number(n, binding == null ? null : binding.decimals());
        }
        return String.valueOf(value);
    }

    public static boolean isEmpty(Object value) {
        return switch (value) {
            case null -> true;
            case String s -> s.isEmpty();
            case Collection<?> c -> c.isEmpty();
            case Map<?, ?> m -> m.isEmpty();
            default -> false;
        };
    }

    /**
     * The wire value for a model value through a {@code map} (FBL §5.2): the first key in document
     * order whose value matches, unless the value being replaced already maps to it. Null when the
     * binding has no map or the map has no such value.
     *
     * @param replaced the wire value being replaced, or null
     */
    public static String wire(AttributeBinding binding, Object value, String replaced) {
        Map<String, String> map = binding.map();
        if (map == null) {
            return null;
        }
        String model = plain(value, binding);
        if (replaced != null && model.equals(map.get(replaced))) {
            return replaced;
        }
        for (Map.Entry<String, String> entry : map.entrySet()) {
            if (entry.getValue().equals(model)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * A date or date-time written with the precision of the value it replaces (FBL §6.3,
     * {@code time: "keep-precision"}); null when either is not a date or date-time.
     */
    public static String keepPrecision(String replaced, String value) {
        Time time = parseTime(value);
        if (time == null) {
            return null;
        }
        Time shape = parseTime(replaced);
        if (shape == null) {
            return null;
        }
        LocalDateTime at = time.at();
        if (!shape.hasTime()) {
            return date(at);
        }
        StringBuilder builder = new StringBuilder(date(at)).append('T');
        digits(builder, at.getHour(), 2).append(':');
        digits(builder, at.getMinute(), 2).append(':');
        digits(builder, at.getSecond(), 2);
        if (shape.fractionDigits() > 0) {
            // A time is kept to a ten-millionth of a second.
            StringBuilder fraction = digits(new StringBuilder(), at.getNano() / 100, 7);
            builder.append('.').append(fraction, 0, Math.min(7, shape.fractionDigits()));
        }
        if (shape.offset() != null) {
            if (shape.offset().equals("Z")) {
                builder.append('Z');
            } else {
                int minutes = Math.abs(time.offsetMinutes());
                builder.append(time.offsetMinutes() < 0 ? '-' : '+');
                digits(builder, minutes / 60, 2).append(':');
                digits(builder, minutes % 60, 2);
            }
        }
        return builder.toString();
    }

    private static String date(LocalDateTime at) {
        StringBuilder builder = new StringBuilder();
        digits(builder, at.getYear(), 4).append('-');
        digits(builder, at.getMonthValue(), 2).append('-');
        return digits(builder, at.getDayOfMonth(), 2).toString();
    }

    private static StringBuilder digits(StringBuilder builder, int value, int width) {
        String text = Integer.toString(value);
        return builder.repeat('0', Math.max(0, width - text.length())).append(text);
    }

    /**
     * A date or date-time as it is written.
     *
     * @param at the date and time on the value's own clock; midnight for a date
     * @param offsetMinutes the value's offset from UTC, 0 when it states none
     * @param fractionDigits the digits of the fraction of a second, as written
     * @param offset the offset as written, {@code Z} in upper case; null when the value states none
     */
    private record Time(LocalDateTime at, int offsetMinutes, boolean hasTime, int fractionDigits, String offset) {
    }

    /** What may follow the seconds: a fraction, then an offset, white space around the offset. */
    private static final Pattern TAIL = Pattern.compile(
            "(?:[.,]([0-9]+))?" + WHITE_SPACE + "*(?:([Zz])|([+-])([0-9]{1,4})(?::([0-9]{1,2}))?)?" + WHITE_SPACE + "*");

    /**
     * Reads {@code yyyy-MM-dd}, alone or followed by {@code T}, {@code t} or a space, then
     * {@code HH:mm:ss}, an optional fraction and an optional offset: {@code Z}, or a sign with the
     * hours, or the hours and the minutes with or without a colon. Null for anything else.
     */
    private static Time parseTime(String text) {
        if (text.length() < 10 || text.charAt(4) != '-' || text.charAt(7) != '-') {
            return null;
        }
        LocalDate date = parseDate(text);
        if (text.length() == 10) {
            return date == null ? null : new Time(date.atStartOfDay(), 0, false, 0, null);
        }
        char separator = text.charAt(10);
        if (!(separator == 'T' || separator == 't' || separator == ' ')) {
            return null;
        }
        String rest = text.substring(19);
        int fraction = 0;
        if (rest.startsWith(".")) {
            fraction = 1;
            while (fraction < rest.length() && isAsciiDigit(rest.charAt(fraction))) {
                fraction++;
            }
            fraction--;
        }
        String offsetText = rest.substring(fraction > 0 ? fraction + 1 : 0);
        String offset = offsetText.isEmpty() ? null : trimWhiteSpace(offsetText);
        if (date == null || text.charAt(13) != ':' || text.charAt(16) != ':') {
            return null;
        }
        int hour = twoDigits(text, 11);
        int minute = twoDigits(text, 14);
        int second = twoDigits(text, 17);
        Matcher tail = TAIL.matcher(rest);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59 || second < 0 || second > 59 || !tail.matches()) {
            return null;
        }
        LocalDateTime at = date.atTime(hour, minute, second);
        if (tail.group(1) != null) {
            // Rounded to a ten-millionth of a second, which may carry into the seconds.
            long ticks = new BigDecimal("0." + tail.group(1)).movePointRight(7).setScale(0, RoundingMode.HALF_EVEN).longValueExact();
            at = at.plusNanos(ticks * 100);
        }
        int offsetMinutes;
        if (tail.group(2) != null) {
            offsetMinutes = 0;
        } else if (tail.group(3) != null) {
            Integer stated = offsetMinutes(tail.group(4), tail.group(5));
            if (stated == null) {
                return null;
            }
            offsetMinutes = tail.group(3).equals("-") ? -stated : stated;
        } else if (offset == null) {
            offsetMinutes = 0;
        } else {
            // Something follows the seconds that is no offset: the first host then reads the time on this machine's clock.
            offsetMinutes = ZoneId.systemDefault().getRules().getOffset(at).getTotalSeconds() / 60;
        }
        // A time before year 1 or after year 9999, on its own clock or on the UTC clock, is none.
        LocalDateTime utc = at.minusMinutes(offsetMinutes);
        if (utc.getYear() < 1 || utc.getYear() > 9999 || at.getYear() > 9999) {
            return null;
        }
        return new Time(at, offsetMinutes, true, fraction, offset != null && (offset.equals("Z") || offset.equals("z")) ? "Z" : offset);
    }

    private static LocalDate parseDate(String text) {
        for (int i = 0; i < 10; i++) {
            if (i != 4 && i != 7 && !isAsciiDigit(text.charAt(i))) {
                return null;
            }
        }
        int year = Integer.parseInt(text, 0, 4, 10);
        if (year < 1) {
            return null;
        }
        try {
            return LocalDate.of(year, twoDigits(text, 5), twoDigits(text, 8));
        } catch (DateTimeException e) {
            return null;
        }
    }

    /**
     * The minutes of an offset, or null when it is none: at most fourteen hours.
     *
     * @param digits the digits after the sign: the hours, or the hours and the minutes when there are three or four
     * @param minutes the digits after a colon, or null
     */
    private static Integer offsetMinutes(String digits, String minutes) {
        int hours = Integer.parseInt(digits);
        int rest = 0;
        if (minutes != null) {
            if (digits.length() > 2) {
                return null;
            }
            rest = Integer.parseInt(minutes);
        } else if (digits.length() > 2) {
            rest = hours % 100;
            hours /= 100;
        }
        if (rest > 59 || hours * 60 + rest > 14 * 60) {
            return null;
        }
        return hours * 60 + rest;
    }

    /** The number two digits at {@code index} write, or -1 when they are not two digits. */
    private static int twoDigits(String text, int index) {
        if (index + 2 > text.length() || !isAsciiDigit(text.charAt(index)) || !isAsciiDigit(text.charAt(index + 1))) {
            return -1;
        }
        return (text.charAt(index) - '0') * 10 + (text.charAt(index + 1) - '0');
    }

    private static String trimWhiteSpace(String text) {
        Matcher matcher = TRIMMED.matcher(text);
        return matcher.matches() ? matcher.group(1) : text;
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    /**
     * Renders an {@code emit} template (FBL §6.2): placeholders {@code {name}} replaced by
     * {@code value}; a {@code [ … ]} segment written only when every placeholder in it
     * is non-empty. Any other <code>{</code> or <code>}</code> is literal.
     *
     * @param value the text of a placeholder by its name; may return null for none
     */
    public static String render(String template, Function<String, String> value) {
        StringBuilder output = new StringBuilder();
        int i = 0;
        while (i < template.length()) {
            char c = template.charAt(i);
            if (c == '[') {
                int close = template.indexOf(']', i + 1);
                if (close > 0) {
                    String segment = template.substring(i + 1, close);
                    boolean[] complete = {true};
                    String rendered = renderPart(segment, name -> {
                        String v = value.apply(name);
                        if (v == null || v.isEmpty()) {
                            complete[0] = false;
                        }
                        return v;
                    });
                    if (complete[0]) {
                        output.append(rendered);
                    }
                    i = close + 1;
                    continue;
                }
            }
            int next = template.indexOf('[', i);
            int end = next < 0 ? template.length() : next;
            output.append(renderPart(template.substring(i, end), value));
            i = end;
            if (next >= 0 && template.indexOf(']', next + 1) < 0) {
                output.append('[');
                i++;
            }
        }
        return output.toString();
    }

    private static String renderPart(String part, Function<String, String> value) {
        StringBuilder output = new StringBuilder();
        int i = 0;
        while (i < part.length()) {
            if (part.charAt(i) == '{') {
                int close = part.indexOf('}', i + 1);
                if (close > i + 1 && isPlaceholderName(part.substring(i + 1, close))) {
                    String v = value.apply(part.substring(i + 1, close));
                    output.append(v == null ? "" : v);
                    i = close + 1;
                    continue;
                }
            }
            output.append(part.charAt(i));
            i++;
        }
        return output.toString();
    }

    private static boolean isPlaceholderName(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if (!(ch >= 'a' && ch <= 'z' || ch >= 'A' && ch <= 'Z' || isAsciiDigit(ch) || ch == '_' || ch == ':' || ch == '-')) {
                return false;
            }
        }
        return true;
    }

    /** The placeholders of a template in order, each with whether it sits inside an optional segment. */
    public static List<EmitPart> parts(String template) {
        List<EmitPart> parts = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int segment = -1;
        int segments = 0;
        for (int i = 0; i < template.length(); i++) {
            char c = template.charAt(i);
            if (c == '[' && segment < 0 && template.indexOf(']', i + 1) > 0) {
                flush(parts, literal, segment);
                segment = segments++;
                continue;
            }
            if (c == ']' && segment >= 0) {
                flush(parts, literal, segment);
                segment = -1;
                continue;
            }
            if (c == '{') {
                int close = template.indexOf('}', i + 1);
                if (close > i + 1 && isPlaceholderName(template.substring(i + 1, close))) {
                    flush(parts, literal, segment);
                    parts.add(new EmitPart(template.substring(i + 1, close), null, segment));
                    i = close;
                    continue;
                }
            }
            literal.append(c);
        }
        flush(parts, literal, segment);
        return Collections.unmodifiableList(parts);
    }

    private static void flush(List<EmitPart> parts, StringBuilder literal, int segment) {
        if (literal.isEmpty()) {
            return;
        }
        parts.add(new EmitPart(null, literal.toString(), segment));
        literal.setLength(0);
    }
}
