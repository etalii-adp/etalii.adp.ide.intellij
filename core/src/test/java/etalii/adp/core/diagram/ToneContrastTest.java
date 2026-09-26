package etalii.adp.core.diagram;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.intellij.ui.JBColor;

import etalii.adp.core.diagram.model.StyleOverride;
import etalii.adp.core.diagram.view.ElementPainter;
import etalii.adp.core.diagram.view.ElementPainter.Colours;

/** FR-006: every tone reads in both themes; a file colour too close to what it is drawn on gets a plate. */
class ToneContrastTest {

    /** WCAG 2.1 AA for text. */
    private static final double MIN_TEXT_CONTRAST = 4.5;

    @AfterEach
    void lightAgain() {
        JBColor.setDark(false);
    }

    private static List<Tone> predefinedTones() throws IllegalAccessException {
        List<Tone> tones = new ArrayList<>();
        for (Field field : Tone.class.getFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getType() == Tone.class) {
                tones.add((Tone) field.get(null));
            }
        }
        return tones;
    }

    private static Color plain(Color colour) {
        return new Color(colour.getRGB());
    }

    @Test
    void everyPredefinedToneKeepsTextContrastInLightAndDark() throws IllegalAccessException {
        List<Tone> tones = predefinedTones();
        assertEquals(8, tones.size());
        for (boolean dark : new boolean[] { false, true }) {
            JBColor.setDark(dark);
            for (Tone tone : tones) {
                double contrast = ElementPainter.contrast(tone.text(), tone.fill());
                assertTrue(contrast >= MIN_TEXT_CONTRAST, (dark ? "dark " : "light ") + tone + ": " + contrast);
                Colours colours = ElementPainter.colours(tone, null, true);
                assertEquals(plain(tone.fill()), plain(colours.fill()));
                assertEquals(plain(tone.text()), plain(colours.text()));
                assertNull(colours.plate());
            }
        }
    }

    @Test
    void aFileTextColourTooCloseToTheCanvasGetsAPlate() {
        Color navy = new Color(0x000080);
        StyleOverride navyText = StyleOverride.colours(null, null, navy);

        JBColor.setDark(false);
        Colours light = ElementPainter.colours(Tone.NEUTRAL, navyText, false);
        assertNull(light.plate(), "navy reads on a light canvas");
        assertEquals(navy, plain(light.text()));

        JBColor.setDark(true);
        Colours dark = ElementPainter.colours(Tone.NEUTRAL, navyText, false);
        assertEquals(navy, plain(dark.text()), "the author's colour is kept");
        assertNotNull(dark.plate(), "navy on a dark canvas gets a plate");
        assertTrue(ElementPainter.contrast(dark.text(), dark.plate()) >= MIN_TEXT_CONTRAST);
    }

    @Test
    void aFileTextColourTooCloseToItsFillGetsAPlate() {
        JBColor.setDark(false);
        Colours colours = ElementPainter.colours(Tone.BLUE, StyleOverride.colours(null, null, Color.WHITE), true);

        assertEquals(Color.WHITE, plain(colours.text()));
        assertNotNull(colours.plate());
        assertTrue(ElementPainter.contrast(colours.text(), colours.plate()) >= MIN_TEXT_CONTRAST);
    }

    @Test
    void aFileFillGetsATextColourThatReadsOnIt() {
        for (boolean dark : new boolean[] { false, true }) {
            JBColor.setDark(dark);
            for (Color fill : List.of(new Color(0x000080), new Color(0xFFFFCC), new Color(0x808080), new Color(0x1E1F22))) {
                Colours colours = ElementPainter.colours(Tone.NEUTRAL, StyleOverride.colours(fill, null, null), true);
                assertEquals(fill, plain(colours.fill()), "the author's fill is kept");
                assertNull(colours.plate());
                assertTrue(ElementPainter.contrast(colours.text(), colours.fill()) >= MIN_TEXT_CONTRAST,
                        (dark ? "dark " : "light ") + fill + ": " + ElementPainter.contrast(colours.text(), colours.fill()));
            }
        }
    }

    @Test
    void anOutlineThatIsNotDrawnHasNoFill() {
        Colours colours = ElementPainter.colours(Tone.BLUE, null, false);
        assertNull(colours.fill());
        assertFalse(ElementPainter.contrast(colours.text(), ElementPainter.CANVAS) < MIN_TEXT_CONTRAST);
    }

    @Test
    void aFileTextColourOnAFileFillIsDrawnAsWritten() {
        Color fill = new Color(0x5D7F99);
        Colours colours = ElementPainter.colours(Tone.NEUTRAL, StyleOverride.colours(fill, null, Color.WHITE), true);
        assertEquals(fill, colours.fill());
        assertEquals(Color.WHITE, colours.text());
        assertNull(colours.plate());
    }
}
