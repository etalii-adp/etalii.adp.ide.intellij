package etalii.adp.freemind.ui;

import static etalii.adp.freemind.MindMapAsserts.key;
import static etalii.adp.freemind.ui.AddNodeTest.MAP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.Page;

/** FR-007, US3-AS3 and the malformed-file edge case: text that is not a map is explained, never lost. */
class FormatProblemTest {

    private static final String B_LINE = "<node CREATED=\"1000\" ID=\"B\" MODIFIED=\"1000\" POSITION=\"right\" TEXT=\"B\"/>";

    /**
     * Name, how the valid map is broken, the one-based line the problem is reported on, the column
     * (or 0 when the XML parser picks it) and a phrase of the explanation. MAP's line 8 is node B's,
     * line 11 is {@code </map>}.
     */
    static Stream<Arguments> problems() {
        return Stream.of(
                Arguments.of("malformed XML", (UnaryOperator<String>) t -> t.replace(B_LINE, B_LINE.replace("/>", " TEXT=\"Again\"/>")),
                        8, 0, "TEXT"),
                Arguments.of("not a map", (UnaryOperator<String>) t -> t.replace("<map ", "<mop ").replace("</map>", "</mop>"), 1, 1,
                        "The root element is <mop>"),
                Arguments.of("two top-level nodes",
                        (UnaryOperator<String>) t -> t.replace("</map>", "<node ID=\"X\" TEXT=\"Second root\"/>\n</map>"), 11, 1,
                        "more than one top-level node"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("problems")
    void aTextEditIntoAnInvalidMapIsExplainedAndKept(String name, UnaryOperator<String> breakIt, int line, int column, String phrase) {
        try (var d = DesignerDriver.openText("problem.mm", MAP, MindMapEditor.ID)) {
            String broken = breakIt.apply(MAP);
            d.editText(breakIt);
            assertEquals(broken, d.text());

            d.showPage(Page.VISUAL);
            assertTrue(d.editor().isVisualPageActive(), "the visual page opens and explains");
            assertTrue(d.problemShown());
            String message = d.editor().problemMessage();
            assertNotNull(message);
            assertTrue(message.startsWith("This file cannot be shown in the designer."), message);
            String where = column > 0 ? "Line " + line + ", column " + column + ":" : "Line " + line + ", column ";
            assertTrue(message.contains(where), "expected '" + where + "' in: " + message);
            assertTrue(message.contains(phrase), message);
            assertFalse(d.editor().viewer().getControl().isVisible(), "no half-drawn map");
            assertEquals(broken, d.text(), "no text is lost");

            Button showText = showTextButton(d);
            assertNotNull(showText, "the problem offers Show text");
            showText.notifyListeners(SWT.Selection, null);
            d.settle();
            assertFalse(d.editor().isVisualPageActive(), "Show text returns to the text page");
            assertEquals(broken, d.text());
            assertTrue(d.isDirty());

            d.editText(text -> MAP);
            d.showPage(Page.VISUAL);
            assertFalse(d.problemShown());
            assertNull(d.editor().problemMessage());
            assertTrue(d.editor().viewer().getControl().isVisible());
            assertNotNull(d.figureOf(key("B")), "fixing the text restores the map");
            assertNull(showTextButton(d));
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("problems")
    void aMalformedFileOpensOnTheTextPageUnmodified(String name, UnaryOperator<String> breakIt, int line, int column, String phrase)
            throws IOException {
        String broken = breakIt.apply(MAP);
        try (var d = DesignerDriver.openText("broken.mm", broken, MindMapEditor.ID)) {
            d.settle();
            assertFalse(d.editor().isVisualPageActive(), "a file that cannot be shown opens on the text page");
            assertEquals(broken, d.text());
            assertFalse(d.isDirty());
            assertTrue(d.problemShown());
            String message = d.editor().problemMessage();
            assertNotNull(message, "the visual page holds the explanation");
            assertTrue(message.contains("Line " + line + ", column "), message);
            assertTrue(message.contains(phrase), message);
            assertArrayEquals(broken.getBytes(UTF_8), Files.readAllBytes(d.file().getLocation().toFile().toPath()),
                    "opening leaves the file as it was");

            d.showPage(Page.VISUAL);
            d.showPage(Page.TEXT);
            assertFalse(d.isDirty(), "looking at the explanation changes nothing");
            assertEquals(broken, d.text());
        }
    }

    /** The visible "Show text" button on the visual page, or {@code null}. */
    private static Button showTextButton(DesignerDriver d) {
        return findButton(d.editor().viewer().getControl().getParent(), "Show text");
    }

    private static Button findButton(Control control, String label) {
        if (control instanceof Button button && label.equals(button.getText()) && button.isVisible()) {
            return button;
        }
        if (control instanceof Composite composite && control.isVisible()) {
            for (Control child : composite.getChildren()) {
                Button found = findButton(child, label);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
