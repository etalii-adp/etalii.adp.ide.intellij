package etalii.adp.fbl.yaml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import etalii.adp.fbl.family.yaml.YamlScalars;

/**
 * FBL §6.3, yaml scalars: "A string is plain-safe when it is not empty; has no leading or trailing
 * whitespace; contains no line break or control character; does not start with any of
 * {@code - ? : , [ ] { } # & * ! | > ' " % @ `}; contains neither {@code : } nor {@code  #} and does
 * not end with {@code :}; and would be read back as the same string under the YAML 1.2 core schema
 * and as a string under YAML 1.1 (so not null, ~, true, false, yes, no, on, off, y, n in any case, a
 * number, or a date or date-time), unless the attribute's DISL type is the type it would read as."
 * The counterparts of standalone's {@code Yaml/YamlScalars.Tests.cs}.
 */
class YamlScalarsTest {

    @ParameterizedTest
    @MethodSource("plainSafeStrings")
    void aPlainSafeStringIsWrittenPlain(String value, boolean safe) {
        assertEquals(safe, YamlScalars.isPlainSafe(value, false));
    }

    static Stream<Arguments> plainSafeStrings() {
        return Stream.of(
                Arguments.of("Discovery", true),
                Arguments.of("Plan the launch", true),
                Arguments.of("a-b:c", true),
                Arguments.of("", false),
                Arguments.of(" lead", false),
                Arguments.of("trail ", false),
                Arguments.of("two\nlines", false),
                Arguments.of("bell\u0007", false),
                Arguments.of("- item", false),
                Arguments.of("?q", false),
                Arguments.of(":x", false),
                Arguments.of(",x", false),
                Arguments.of("[x", false),
                Arguments.of("]x", false),
                Arguments.of("{x", false),
                Arguments.of("}x", false),
                Arguments.of("#x", false),
                Arguments.of("&x", false),
                Arguments.of("*x", false),
                Arguments.of("!x", false),
                Arguments.of("|x", false),
                Arguments.of(">x", false),
                Arguments.of("'x", false),
                Arguments.of("\"x", false),
                Arguments.of("%x", false),
                Arguments.of("@x", false),
                Arguments.of("`x", false),
                Arguments.of("key: value", false),
                Arguments.of("text #comment", false),
                Arguments.of("ends:", false),
                Arguments.of("null", false),
                Arguments.of("~", false),
                Arguments.of("True", false),
                Arguments.of("FALSE", false),
                Arguments.of("yes", false),
                Arguments.of("No", false),
                Arguments.of("on", false),
                Arguments.of("OFF", false),
                Arguments.of("y", false),
                Arguments.of("N", false),
                Arguments.of("42", false),
                Arguments.of("-1.5e3", false),
                Arguments.of("0x1F", false),
                Arguments.of("1_000", false),
                Arguments.of("12:30", false),
                Arguments.of("2026-10-01", false),
                Arguments.of("2026-10-01T09:00:00", false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-10-01", "2026-10-01T09:00:00"})
    void aDateIsPlainSafeWhenTheAttributeIsADate(String value) {
        // "unless the attribute's DISL type is the type it would read as".
        assertTrue(YamlScalars.isPlainSafe(value, true));
    }

    @ParameterizedTest
    @MethodSource("doubleQuotedStrings")
    void aDoubleQuotedStringEscapesBackslashQuoteAndControlCharacters(String value, String written) {
        assertEquals(written, YamlScalars.doubleQuoted(value));
    }

    static Stream<Arguments> doubleQuotedStrings() {
        return Stream.of(
                Arguments.of("a\"b\\c\n", "\"a\\\"b\\\\c\\n\""),
                Arguments.of("tab\t", "\"tab\\t\""));
    }

    @Test
    void aSingleQuotedStringDoublesItsQuotes() {
        assertEquals("'it''s'", YamlScalars.singleQuoted("it's"));
    }
}
