package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.ui.JBColor;
import com.intellij.ui.scale.JBUIScale;

import etalii.adp.core.NodeView;
import etalii.adp.testing.DesignerDriver;

/** FR-016, US1-AS5: legible in light and dark themes, sharp at any scale. */
@RunWith(JUnit4.class)
public class ThemeTest extends FileEditorManagerTestCase {

    /** WCAG 2.1 non-text contrast, which the designer holds text and lines to. */
    private static final double MIN_CONTRAST = 3.0;

    static final String MAP = """
            <map version="1.0.1">
            <node ID="R" TEXT="Root">
            <node ID="PLAIN" POSITION="right" TEXT="Plain"/>
            <node COLOR="#000080" ID="NAVY" POSITION="right" TEXT="Navy"/>
            <node COLOR="#ffff66" ID="PALE" POSITION="right" TEXT="Pale"/>
            <node BACKGROUND_COLOR="#ffffcc" COLOR="#000000" ID="CARD" POSITION="left" TEXT="Card"/>
            <node BACKGROUND_COLOR="#ccffcc" ID="MINT" POSITION="left" TEXT="Mint"/>
            <node HGAP="100" ID="WIDE" POSITION="left" TEXT="Wide">
            <font NAME="Dialog" SIZE="16"/>
            </node>
            </node>
            </map>
            """;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        try {
            JBColor.setDark(false);
            JBUIScale.setUserScaleFactorForTest(1f);
        } finally {
            super.tearDown();
        }
    }

    @Test
    public void everyDefaultColourContrastsInLightAndDark() {
        for (boolean dark : new boolean[] { false, true }) {
            JBColor.setDark(dark);
            Color background = NodePainter.CANVAS_BACKGROUND;
            for (Color colour : NodePainter.defaultColours()) {
                double contrast = NodePainter.contrast(colour, background);
                assertTrue((dark ? "dark: " : "light: ") + colour + " has " + contrast, contrast >= MIN_CONTRAST);
            }
        }
    }

    @Test
    public void everyNodeIsLegibleUnderDarcula() {
        for (boolean dark : new boolean[] { false, true }) {
            JBColor.setDark(dark);
            try (var d = DesignerDriver.openText(myFixture, (dark ? "dark" : "light") + ".mm", MAP)) {
                for (String id : new String[] { "R", "PLAIN", "NAVY", "PALE", "CARD", "MINT", "WIDE" }) {
                    NodeView view = d.viewOf(key(id));
                    Color behind = view.background() != null ? view.background() : NodePainter.CANVAS_BACKGROUND;
                    double contrast = NodePainter.contrast(view.foreground(), behind);
                    assertTrue((dark ? "dark: " : "light: ") + id + " has " + contrast, contrast >= MIN_CONTRAST);
                }
            }
        }
    }

    @Test
    public void aLowContrastFileColourGetsAPlate() {
        JBColor.setDark(true);
        try (var d = DesignerDriver.openText(myFixture, "plate.mm", MAP)) {
            NodeView navy = d.viewOf(key("NAVY"));
            assertEquals("the author's colour is kept", new Color(0x000080), new Color(navy.foreground().getRGB()));
            assertNotNull("navy on a dark canvas is drawn on a plate", navy.background());

            NodeView card = d.viewOf(key("CARD"));
            assertEquals("a node with its own background is its own plate", new Color(0xffffcc), new Color(card.background().getRGB()));
            assertEquals(new Color(0x000000), new Color(card.foreground().getRGB()));
            assertNull(d.viewOf(key("PLAIN")).background());
        }
        JBColor.setDark(false);
        try (var d = DesignerDriver.openText(myFixture, "no-plate.mm", MAP)) {
            assertNull("navy on a light canvas needs no plate", d.viewOf(key("NAVY")).background());
            assertNotNull("pale yellow on a light canvas does", d.viewOf(key("PALE")).background());
        }
    }

    @Test
    public void sizesFollowTheIdeScale() {
        int gapAtOne;
        int fontAtOne;
        int heightAtOne;
        try (var d = DesignerDriver.openText(myFixture, "scale-1.mm", MAP)) {
            gapAtOne = d.viewOf(key("R")).bounds().x - (int) d.viewOf(key("WIDE")).bounds().getMaxX();
            fontAtOne = d.viewOf(key("WIDE")).font().getSize();
            heightAtOne = d.viewOf(key("WIDE")).bounds().height;
        }
        assertEquals(100, gapAtOne);
        assertEquals(16, fontAtOne);

        JBUIScale.setUserScaleFactorForTest(2f);
        try (var d = DesignerDriver.openText(myFixture, "scale-2.mm", MAP)) {
            assertEquals(200, d.viewOf(key("R")).bounds().x - (int) d.viewOf(key("WIDE")).bounds().getMaxX());
            assertEquals(32, d.viewOf(key("WIDE")).font().getSize());
            assertTrue(d.viewOf(key("WIDE")).bounds().height > heightAtOne * 3 / 2);
        }
    }

    @Test
    public void theCanvasPaintsInBothThemes() {
        for (boolean dark : new boolean[] { false, true }) {
            JBColor.setDark(dark);
            try (var d = DesignerDriver.openText(myFixture, (dark ? "paint-dark" : "paint-light") + ".mm", MAP)) {
                MindMapCanvas canvas = LayoutTest.designer(d).canvas();
                canvas.setSize(canvas.getPreferredSize());
                BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
                var graphics = image.createGraphics();
                try {
                    canvas.paint(graphics);
                } finally {
                    graphics.dispose();
                }
                assertEquals(new Color(NodePainter.CANVAS_BACKGROUND.getRGB()), new Color(image.getRGB(0, 0)));
            }
        }
    }
}
