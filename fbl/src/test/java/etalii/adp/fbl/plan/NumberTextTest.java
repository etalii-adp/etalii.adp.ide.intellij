package etalii.adp.fbl.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * FBL §6.3, numbers in new text. No test of standalone covers its number writing on its own, so
 * every expected string here is what standalone's code gives for the value. That is the form of
 * ECMAScript's {@code Number.prototype.toString} except where the rows say otherwise.
 */
class NumberTextTest {

    @ParameterizedTest
    @MethodSource("shortestNumbers")
    void aNumberIsWrittenInItsShortestForm(double value, String written) {
        assertEquals(written, NumberText.number(value, null));
    }

    static Stream<Arguments> shortestNumbers() {
        return Stream.of(
                // A whole double has no fraction.
                Arguments.of(640.0, "640"),
                Arguments.of(-100.0, "-100"),
                Arguments.of(0.0, "0"),
                Arguments.of(-0.0, "0"),
                // The exponent form starts at 1e21, spelled with a sign and no leading zero.
                Arguments.of(1e21, "1e+21"),
                Arguments.of(1e22, "1e+22"),
                Arguments.of(1.7976931348623157e308, "1.7976931348623157e+308"),
                Arguments.of(1e20, "100000000000000000000"),
                Arguments.of(1.2345e20, "123450000000000000000"),
                // Small values.
                Arguments.of(0.000001, "0.000001"),
                Arguments.of(0.0000015, "0.0000015"),
                Arguments.of(-1e-7, "-1e-7"),
                Arguments.of(-1.5e-7, "-1.5e-7"),
                // The shortest digits that read back as the same double.
                Arguments.of(0.1 + 0.2, "0.30000000000000004"),
                Arguments.of(12.5, "12.5"),
                Arguments.of(3.14159, "3.14159"),
                Arguments.of(-4622.93173, "-4622.93173"),
                Arguments.of(1234567890123456.5, "1234567890123456.5"));
    }

    /**
     * A value less than a millionth above a whole number is written as a whole number is: as a
     * decimal of at most fifteen significant digits and never in exponent form.
     */
    @ParameterizedTest
    @MethodSource("numbersWrittenAsStandaloneWritesThem")
    void aNumberIsWrittenAsStandaloneWritesItWhereEcmaScriptDiffers(double value, String written) {
        assertEquals(written, NumberText.number(value, null));
    }

    static Stream<Arguments> numbersWrittenAsStandaloneWritesThem() {
        return Stream.of(
                // ECMAScript: 1e-7, 1.5e-7, 1e-10.
                Arguments.of(1e-7, "0.0000001"),
                Arguments.of(1.5e-7, "0.00000015"),
                Arguments.of(1e-10, "0.0000000001"),
                Arguments.of(640.0000001, "640.0000001"),
                // ECMAScript: 1e-29, 1e-300, 5e-324.
                Arguments.of(1e-28, "0.0000000000000000000000000001"),
                Arguments.of(1e-29, "0"),
                Arguments.of(1e-300, "0"),
                Arguments.of(5e-324, "0"),
                // ECMAScript: 123456789012345680, 9007199254740992, 123456789012345670000.
                Arguments.of(123456789012345678.0, "123456789012346000"),
                Arguments.of(9007199254740992.0, "9007199254740990"),
                Arguments.of(1.2345678901234567e20, "123456789012346000000"));
    }

    @ParameterizedTest
    @MethodSource("fixedNumbers")
    void aNumberWithDecimalsIsRoundedHalfAwayFromZeroAndTrimmed(double value, int decimals, String written) {
        assertEquals(written, NumberText.number(value, decimals));
    }

    static Stream<Arguments> fixedNumbers() {
        return Stream.of(
                Arguments.of(12.3456, 3, "12.346"),
                Arguments.of(0.0005, 3, "0.001"),
                Arguments.of(-0.0005, 3, "-0.001"),
                Arguments.of(2.5, 0, "3"),
                Arguments.of(-2.5, 0, "-3"),
                Arguments.of(0.125, 2, "0.13"),
                // Trailing zeros and a trailing point are dropped.
                Arguments.of(1.0, 3, "1"),
                Arguments.of(12.5, 3, "12.5"),
                Arguments.of(640.0, 3, "640"),
                Arguments.of(0.1 + 0.2, 3, "0.3"),
                // What rounds to nothing is 0, never -0.
                Arguments.of(-0.0, 3, "0"),
                Arguments.of(-0.0001, 3, "0"),
                Arguments.of(1e-7, 3, "0"),
                // The half is judged on the double times ten to the decimals, as standalone judges it.
                Arguments.of(1.005, 2, "1"),
                Arguments.of(1e21, 3, "1000000000000000000000"));
    }

    @Test
    void aNumberThatIsNotFiniteIsNotWritten() {
        assertThrows(IllegalArgumentException.class, () -> NumberText.number(Double.NaN, null));
        assertThrows(IllegalArgumentException.class, () -> NumberText.number(Double.POSITIVE_INFINITY, null));
    }
}
