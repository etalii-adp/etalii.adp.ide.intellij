package etalii.adp.freemind.ui;

import static etalii.adp.freemind.FreeMindAsserts.key;
import static etalii.adp.freemind.ui.TextVisualSyncTest.MAP;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;

import java.awt.Component;
import java.awt.Container;
import java.io.IOException;
import java.util.List;
import java.util.function.UnaryOperator;

import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;
import com.intellij.ui.HyperlinkLabel;

import etalii.adp.testing.DesignerDriver;
import etalii.adp.testing.Layout;

/** FR-009, US1-AS4 and spec 001 FR-007: text that is not a map is explained, never lost or changed. */
@RunWith(JUnit4.class)
public class FormatProblemTest extends FileEditorManagerTestCase {

    private static final String B_LINE = "<node CREATED=\"1000\" ID=\"B\" MODIFIED=\"1000\" POSITION=\"right\" TEXT=\"B\"/>";

    /**
     * How the valid map is broken, the one-based line the problem is reported on, the column (0
     * when the XML parser picks it), a phrase of the explanation, and whether the result is still
     * claimed as a FreeMind map when opened. MAP's line 8 is node B's, line 11 is {@code </map>}.
     */
    private record Problem(String name, UnaryOperator<String> breakIt, int line, int column, String phrase, boolean claimed) {
    }

    private static final List<Problem> PROBLEMS = List.of(
            new Problem("malformed XML", t -> t.replace(B_LINE, B_LINE.replace("/>", " TEXT=\"Again\"/>")), 8, 0, "TEXT", true),
            new Problem("not a map", t -> t.replace("<map ", "<mop ").replace("</map>", "</mop>"), 1, 1, "The root element is <mop>", false),
            new Problem("no top-level node", t -> t.substring(0, t.indexOf("<node")) + "</map>\n", 1, 1, "no node", true),
            new Problem("two top-level nodes", t -> t.replace("</map>", "<node ID=\"X\" TEXT=\"Second root\"/>\n</map>"), 11, 1,
                    "more than one top-level node", true),
            new Problem("DOCTYPE", t -> "<!DOCTYPE map>\n" + t, 1, 1, "DOCTYPE", true));

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void aTextEditIntoAnInvalidMapIsExplainedAndKept() {
        for (Problem problem : PROBLEMS) {
            try (var d = DesignerDriver.openText(myFixture, "problem-" + PROBLEMS.indexOf(problem) + ".mm", MAP)) {
                String broken = problem.breakIt().apply(MAP);
                d.showLayout(Layout.TEXT);
                d.editText(problem.breakIt());
                assertEquals(problem.name(), broken, d.text());

                d.showLayout(Layout.DESIGNER);
                assertTrue(problem.name(), d.problemShown());
                String message = d.designer().problemMessage();
                assertExplains(problem, message);
                assertNull(problem.name() + ": no half-drawn map", d.designer().model());
                assertFalse(problem.name(), SwingUtilities.getAncestorOfClass(JScrollPane.class, LayoutTest.designer(d).canvas()).isVisible());
                assertNull(problem.name(), d.viewOf(key("B")));
                assertEquals(problem.name() + ": no text is lost", broken, d.text());

                HyperlinkLabel showText = showTextLink(d);
                assertNotNull(problem.name() + ": the problem offers Show Text", showText);
                showText.doClick();
                d.settle();
                assertEquals(problem.name(), Layout.TEXT, d.layout());
                assertEquals(broken, d.text());
                assertTrue(d.isModified());

                d.editText(text -> MAP);
                d.showLayout(Layout.DESIGNER);
                assertFalse(problem.name(), d.problemShown());
                assertNull(d.designer().problemMessage());
                assertNotNull(problem.name() + ": fixing the text restores the map", d.viewOf(key("B")));
                assertNull(showTextLink(d));
            }
        }
    }

    @Test
    public void aMalformedFileOpensOnTheTextPageUnmodified() throws IOException {
        for (Problem problem : PROBLEMS) {
            String broken = problem.breakIt().apply(MAP);
            try (var d = DesignerDriver.openText(myFixture, "broken-" + PROBLEMS.indexOf(problem) + ".mm", broken)) {
                if (!problem.claimed()) {
                    assertNull(problem.name() + " is not a FreeMind map, so it opens as without the plug-in", d.designer());
                    assertArrayEquals(broken.getBytes(UTF_8), d.file().contentsToByteArray());
                    continue;
                }
                assertEquals(problem.name() + ": a file that cannot be shown opens on the text", Layout.TEXT, d.layout());
                assertEquals(broken, d.text());
                assertFalse(problem.name(), d.isModified());
                assertTrue(problem.name(), d.problemShown());
                assertExplains(problem, d.designer().problemMessage());
                assertArrayEquals(problem.name() + ": opening leaves the file as it was", broken.getBytes(UTF_8), d.file().contentsToByteArray());

                d.showLayout(Layout.DESIGNER);
                d.showLayout(Layout.TEXT);
                assertFalse(problem.name() + ": looking at the explanation changes nothing", d.isModified());
                assertEquals(broken, d.text());
                assertArrayEquals(broken.getBytes(UTF_8), d.savedBytes());
            }
        }
    }

    private static void assertExplains(Problem problem, String message) {
        assertNotNull(problem.name(), message);
        String where = problem.column() > 0 ? "Line " + problem.line() + ", column " + problem.column() + ")" : "Line " + problem.line() + ", column ";
        assertTrue(problem.name() + ": expected '" + where + "' in: " + message, message.contains(where));
        assertTrue(problem.name() + ": " + message, message.contains(problem.phrase()));
    }

    /** The visible "Show Text" link of the problem panel, or {@code null}. */
    private static HyperlinkLabel showTextLink(DesignerDriver d) {
        return find(d.designer().getComponent());
    }

    private static HyperlinkLabel find(Component component) {
        if (component instanceof HyperlinkLabel label && label.getText().contains("Show Text") && component.isVisible()) {
            return label;
        }
        if (component instanceof Container container && component.isVisible()) {
            for (Component child : container.getComponents()) {
                HyperlinkLabel found = find(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
