package etalii.adp.drawio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** T101: a draw.io style is read by key and flag, and edited one key at a time without disturbing the rest. */
class StyleTest {

    private static final String ELLIPSE = "ellipse;whiteSpace=wrap;html=1;fillColor=#dae8fc;strokeColor=#6c8ebf;";

    @Test
    void readsKeysAndFlags() {
        Style style = new Style(ELLIPSE);
        assertEquals("#dae8fc", style.get("fillColor"));
        assertEquals("1", style.get("html"));
        assertNull(style.get("ellipse"), "a flag has no value");
        assertNull(style.get("fontColor"));
        assertTrue(style.has("ellipse"));
        assertTrue(style.has("whiteSpace"), "a key counts as present");
        assertFalse(style.has("rhombus"));
        assertEquals("ellipse", style.name());
        assertNull(new Style("rounded=0;html=1").name(), "a style that starts with a key has no name");
        assertEquals("text", new Style("text;html=1;").name());
        assertNull(new Style(null).get("html"));
        assertEquals("", new Style(null).text());
    }

    @Test
    void settingAKeyReplacesOnlyItsValue() {
        assertEquals("ellipse;whiteSpace=wrap;html=1;fillColor=#FF0000;strokeColor=#6c8ebf;", new Style(ELLIPSE).with("fillColor", "#FF0000").text());
        assertEquals("rounded=1;html=1", new Style("rounded=0;html=1").with("rounded", "1").text());
    }

    @Test
    void addingAKeyKeepsTheTrailingSeparatorAsItIs() {
        assertEquals(ELLIPSE + "dashed=1;", new Style(ELLIPSE).with("dashed", "1").text());
        assertEquals("rounded=0;html=1;dashed=1", new Style("rounded=0;html=1").with("dashed", "1").text());
        assertEquals("dashed=1;", new Style("").with("dashed", "1").text());
        assertEquals("dashed=1;", new Style(null).with("dashed", "1").text());
    }

    @Test
    void removingAKeyKeepsTheOthersInOrder() {
        assertEquals("ellipse;whiteSpace=wrap;html=1;strokeColor=#6c8ebf;", new Style(ELLIPSE).with("fillColor", null).text());
        assertEquals("ellipse;whiteSpace=wrap;html=1;fillColor=#dae8fc;", new Style(ELLIPSE).with("strokeColor", "").text());
        assertEquals("rounded=0", new Style("rounded=0;html=1").with("html", null).text());
        assertEquals("", new Style("html=1").with("html", null).text());
        assertEquals(ELLIPSE, new Style(ELLIPSE).with("dashed", null).text(), "removing an absent key changes nothing");
    }

    @Test
    void unknownKeysAndOddSpellingsSurvive() {
        String odd = "shape=mxgraph.flowchart.start_1;;verticalLabelPosition=bottom;points=[[0,0.5],[1,0.5]];";
        assertEquals(odd.replace("bottom", "top"), new Style(odd).with("verticalLabelPosition", "top").text());
        assertEquals("mxgraph.flowchart.start_1", new Style(odd).get("shape"));
        assertEquals("[[0,0.5],[1,0.5]]", new Style(odd).get("points"));
    }
}
