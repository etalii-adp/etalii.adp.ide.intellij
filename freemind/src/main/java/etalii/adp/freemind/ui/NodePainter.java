package etalii.adp.freemind.ui;

import java.awt.Color;
import java.util.List;

import com.intellij.ui.JBColor;

import etalii.adp.core.diagram.Tone;
import etalii.adp.core.diagram.view.ElementPainter;

/**
 * How FreeMind maps look on the shared canvas (spec 001 FR-014, FR-016; research R19): the theme's
 * colours ({@link JBColor} pairs), the tones {@link FreeMindDefinition} declares, and the glyphs
 * of a node's indicators. The canvas paints them; a file colour is kept and gets a plate when it
 * would not read (research R8).
 */
public final class NodePainter {

    public static final JBColor CANVAS_BACKGROUND = ElementPainter.CANVAS;
    public static final JBColor TEXT = ElementPainter.TEXT;
    public static final JBColor CONNECTOR = new JBColor(0x7D818A, 0x868A91);
    public static final JBColor ARROW_LINK = new JBColor(0x2154B8, 0x6C9BF5);
    public static final JBColor SELECTION = ElementPainter.SELECTION;
    public static final JBColor ROOT_BORDER = new JBColor(0x6F737A, 0x9DA0A8);

    /** The root's ellipse. */
    static final Tone ROOT = Tone.custom(CANVAS_BACKGROUND, ROOT_BORDER, TEXT);
    /** A fork: no outline, only the file's background colour and the underline. */
    static final Tone FORK = Tone.custom(CANVAS_BACKGROUND, CANVAS_BACKGROUND, TEXT);
    static final Tone BUBBLE = Tone.custom(CANVAS_BACKGROUND, CONNECTOR, TEXT);
    static final Tone BRANCH = Tone.custom(CANVAS_BACKGROUND, CONNECTOR, TEXT);
    static final Tone LINK = Tone.custom(CANVAS_BACKGROUND, ARROW_LINK, TEXT);

    static final String LINK_GLYPH = "🔗";
    static final String NOTE_GLYPH = "📝";
    static final String FOLD_GLYPH = "⊕";

    /** A part of a node that answers a click or shows a tooltip. */
    public enum Indicator {
        LINK, NOTE, FOLDED
    }

    private NodePainter() {
    }

    /** The default colours, each of which has at least 3:1 contrast with {@link #CANVAS_BACKGROUND} in either theme. */
    public static List<Color> defaultColours() {
        return List.of(TEXT, CONNECTOR, ARROW_LINK, SELECTION, ROOT_BORDER);
    }

    /** The WCAG 2.1 contrast ratio of two colours, 1 to 21. */
    public static double contrast(Color a, Color b) {
        return ElementPainter.contrast(a, b);
    }
}
