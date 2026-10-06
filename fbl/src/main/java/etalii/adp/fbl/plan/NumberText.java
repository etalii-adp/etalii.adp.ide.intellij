package etalii.adp.fbl.plan;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * A number as FBL §6.3 writes it: with {@code shortest}, an integer without a fraction and any
 * other number in ECMAScript's {@code Number.prototype.toString} form; with {@code {decimals: n}},
 * rounded halves away from zero, trailing zeros and a trailing point dropped.
 *
 * <p>Where the first host departs from that form, this writes what the first host writes, so that
 * every host writes the same bytes: a value less than a millionth above a whole number, the whole
 * number included, is written as a decimal of at most fifteen significant digits.
 */
public final class NumberText {

    /** A fraction smaller than this, counted up from the whole number below, makes a value whole. */
    private static final double TOLERANCE = 0.000001f;

    /** The largest number of decimals a value is rounded to. */
    private static final int MAX_DECIMALS = 15;

    /** From here on a double is whole and is written as it is, without rounding. */
    private static final double ROUND_LIMIT = 1e16;

    /** The largest scale of the fifteen-digit decimal. */
    private static final int MAX_SCALE = 28;

    private static final double[] POWERS_OF_TEN = new double[MAX_SCALE + 1];

    static {
        for (int i = 0; i < POWERS_OF_TEN.length; i++) {
            POWERS_OF_TEN[i] = Double.parseDouble("1e" + i);
        }
    }

    private NumberText() {
    }

    /** The number as text: {@link #shortest} when {@code decimals} is null, else {@link #fixed}. */
    public static String number(double value, Integer decimals) {
        return decimals == null ? shortest(value) : fixed(value, decimals);
    }

    /**
     * The value rounded to {@code decimals} decimals, a half away from zero, without trailing zeros
     * or a trailing point. The half is judged on the double times ten to the power of the decimals.
     */
    public static String fixed(double value, int decimals) {
        if (decimals < 0 || decimals > MAX_DECIMALS) {
            throw new IllegalArgumentException("Rounding digits must be between 0 and 15, inclusive.");
        }
        if (Double.isNaN(value)) {
            return "NaN";
        }
        if (Double.isInfinite(value)) {
            return value > 0 ? "Infinity" : "-Infinity";
        }
        double rounded = value;
        if (Math.abs(value) < ROUND_LIMIT) {
            double power = POWERS_OF_TEN[decimals];
            double scaled = value * power;
            // The largest double below a half: adding a half itself would round 0.49999999999999994 up.
            double whole = scaled + Math.copySign(0.49999999999999994, scaled);
            whole = whole < 0 ? Math.ceil(whole) : Math.floor(whole);
            rounded = whole / power;
        }
        // The exact digits of the double, so a rounded value that is not quite the decimal it stands for shows it.
        String text = new BigDecimal(rounded).setScale(decimals, RoundingMode.HALF_EVEN).toPlainString();
        if (rounded < 0 && !text.startsWith("-")) {
            text = "-" + text;
        }
        if (text.indexOf('.') >= 0) {
            text = trimEnd(trimEnd(text, '0'), '.');
        }
        return text.equals("-0") ? "0" : text;
    }

    /** The shortest text that reads back as {@code value}. Throws {@link IllegalArgumentException} when it is not finite. */
    public static String shortest(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("A number in a body is finite.");
        }
        if (value == 0) {
            return "0";
        }
        if (Math.abs(Math.floor(value) - value) < TOLERANCE && Math.abs(value) < 1e21) {
            return fifteenDigits(value);
        }
        // The shortest digits that read back as the value, and the power of ten of the first of them.
        BigDecimal digits = new BigDecimal(Double.toString(Math.abs(value))).stripTrailingZeros();
        int power = digits.precision() - digits.scale() - 1;
        String sign = value < 0 ? "-" : "";
        if (power >= -6 && power < 21) {
            return sign + digits.toPlainString();
        }
        // ECMAScript writes 1e+21 and 1e-7.
        String mantissa = digits.unscaledValue().toString();
        if (mantissa.length() > 1) {
            mantissa = mantissa.charAt(0) + "." + mantissa.substring(1);
        }
        return sign + mantissa + "e" + (power < 0 ? "-" : "+") + Math.abs(power);
    }

    /**
     * The value as a decimal of at most fifteen significant digits and at most 28 decimals, rounded
     * to nearest with a half going to the even digit: what is smaller than that is 0.
     */
    private static String fifteenDigits(double value) {
        int exponent = (int) ((Double.doubleToRawLongBits(value) >> 52) & 0x7FF) - 1022;
        // The most the value can be scaled by is ten to the 28th, which is just over two to the 93rd.
        if (exponent < -94) {
            return "0";
        }
        if (exponent > 96) {
            throw new ArithmeticException("The number is too large to write as a decimal.");
        }
        double scaled = Math.abs(value);
        // The power of ten that brings the value to fifteen digits: the binary exponent times log10(2), in 16 bits.
        int power = 14 - ((exponent * 19728) >> 16);
        if (power >= 0) {
            if (power > MAX_SCALE) {
                power = MAX_SCALE;
            }
            scaled *= POWERS_OF_TEN[power];
        } else if (power != -1 || scaled >= 1e15) {
            scaled /= POWERS_OF_TEN[-power];
        } else {
            power = 0;
        }
        if (scaled < 1e14 && power < MAX_SCALE) {
            scaled *= 10;
            power++;
        }
        long mantissa = (long) Math.rint(scaled);
        if (mantissa == 0) {
            return "0";
        }
        BigDecimal decimal;
        if (power < 0) {
            decimal = new BigDecimal(BigInteger.valueOf(mantissa).multiply(BigInteger.TEN.pow(-power)));
        } else {
            // Trailing zeros leave the fraction, fourteen of them at most.
            int most = Math.min(power, 14);
            while (most > 0 && mantissa % 10 == 0) {
                mantissa /= 10;
                power--;
                most--;
            }
            decimal = new BigDecimal(BigInteger.valueOf(mantissa), power);
        }
        return (value < 0 ? "-" : "") + decimal.toPlainString();
    }

    private static String trimEnd(String text, char c) {
        int end = text.length();
        while (end > 0 && text.charAt(end - 1) == c) {
            end--;
        }
        return text.substring(0, end);
    }
}
