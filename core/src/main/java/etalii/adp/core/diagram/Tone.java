package etalii.adp.core.diagram;

import java.awt.Color;

import com.intellij.ui.JBColor;

/**
 * A theme-safe colour set for an element or connection type (FR-006, research R10): fill, border
 * and text, each a light and dark pair chosen for a text contrast of at least 4.5:1 on both themes.
 */
public final class Tone {

    public static final Tone NEUTRAL = predefined("neutral", 0xF7F8FA, 0x6C707E, 0x2B2D30, 0x6F737A);
    public static final Tone BLUE = predefined("blue", 0xE6EEFC, 0x3574F0, 0x25324D, 0x548AF7);
    public static final Tone GREEN = predefined("green", 0xE6F4EA, 0x208A3C, 0x253627, 0x57965C);
    public static final Tone YELLOW = predefined("yellow", 0xFFF6D6, 0xC29100, 0x3D3223, 0xD6AE58);
    public static final Tone ORANGE = predefined("orange", 0xFDEBDD, 0xE56D17, 0x45322B, 0xE08855);
    public static final Tone RED = predefined("red", 0xFDE8E8, 0xDB3B4B, 0x402929, 0xDB5C5C);
    public static final Tone PURPLE = predefined("purple", 0xF2EDFB, 0x8A57D6, 0x2F2936, 0xA571E6);
    public static final Tone GREY = predefined("grey", 0xEBECF0, 0x818594, 0x393B40, 0x868A91);

    private static final int LIGHT_TEXT = 0x1E1F22;
    private static final int DARK_TEXT = 0xDFE1E5;

    private final String name;
    private final JBColor fill;
    private final JBColor border;
    private final JBColor text;

    private Tone(String name, JBColor fill, JBColor border, JBColor text) {
        this.name = name;
        this.fill = fill;
        this.border = border;
        this.text = text;
    }

    private static Tone predefined(String name, int lightFill, int lightBorder, int darkFill, int darkBorder) {
        return new Tone(name, new JBColor(new Color(lightFill), new Color(darkFill)), new JBColor(new Color(lightBorder), new Color(darkBorder)),
                new JBColor(new Color(LIGHT_TEXT), new Color(DARK_TEXT)));
    }

    /** A tone of the designer's own; the designer answers for its contrast. */
    public static Tone custom(JBColor fill, JBColor border, JBColor text) {
        return new Tone("custom", fill, border, text);
    }

    public JBColor fill() {
        return fill;
    }

    public JBColor border() {
        return border;
    }

    public JBColor text() {
        return text;
    }

    @Override
    public String toString() {
        return name;
    }
}