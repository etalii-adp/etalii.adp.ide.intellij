package etalii.adp.core.diagram.view;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Set;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditorProvider;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.AdpToolFileEditor.ToolState;
import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleMapping;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.settings.AdpSettings;
import etalii.adp.core.settings.AdpSettingsListener;
import etalii.adp.core.settings.CanvasOption;
import etalii.adp.core.settings.CanvasOptions;
import etalii.adp.core.settings.FreshSettings;
import etalii.adp.testing.DiagramDriver;

/**
 * T036 (FR-006, FR-011, FR-012, SC-003, acceptance 3.1 and 3.2), with two sample files open: the
 * grid shows after Apply without reopening, moving snaps to the grid spacing only with snapping
 * on, the opening zoom applies unless the file remembers its own, a diagram that fixes an option
 * keeps it, and none of this changes a document, its modified state or its undo history.
 */
@RunWith(JUnit4.class)
public class CanvasOptionsLiveTest extends FileEditorManagerTestCase {

    private static final String DIAGRAM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <sample>
              <box id="a" type="decision" x="200" y="200">A</box>
            </sample>
            """;

    /** The sample diagram for {@code .adpfixed}, keeping snapping on whatever the user chose. */
    static final class SnapFixed extends DiagramEditorProvider {

        SnapFixed() {
            super(SampleDefinition.builder().view(v -> v.fix(CanvasOption.SNAP_TO_GRID, true)), SampleMapping::new);
        }

        @Override
        protected Set<String> extensions() {
            return Set.of("adpfixed");
        }

        @Override
        protected boolean sniff(byte[] head) {
            return true;
        }

        @Override
        protected String toolName() {
            return "Snap Fixed Diagram";
        }

        @Override
        public String getEditorTypeId() {
            return "etalii.adp.snapfixed";
        }
    }

    private AdpSettings settings;

    @Override
    public void setUp() {
        super.setUp();
        settings = FreshSettings.install(getTestRootDisposable());
        SampleProvider.register(getTestRootDisposable());
    }

    private DiagramDriver open(String name) {
        return DiagramDriver.openText(myFixture, name, DIAGRAM);
    }

    /** What Apply on the ADP page does: store, then tell open diagrams once. */
    private void apply(CanvasOptions options) {
        settings.setCanvas(options);
        ApplicationManager.getApplication().getMessageBus().syncPublisher(AdpSettingsListener.TOPIC).settingsChanged();
    }

    private static Color pixel(DiagramDriver d, int x, int y) {
        DiagramCanvas canvas = d.tool().canvas();
        canvas.setSize(600, 500);
        BufferedImage image = new BufferedImage(canvas.getWidth(), canvas.getHeight(), BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            canvas.paint(graphics);
        } finally {
            graphics.dispose();
        }
        return new Color(image.getRGB(x, y));
    }

    private void assertUntouched(DiagramDriver d, String text) {
        assertEquals(text, d.driver().text());
        assertFalse(d.driver().isModified());
        assertFalse(UndoManager.getInstance(getProject()).isUndoAvailable(d.driver().editor()));
    }

    @Test
    public void theGridShowsInOpenDiagramsAfterApplyWithoutReopening() {
        try (var first = open("first.adpsample"); var second = open("second.adpsample")) {
            Color background = new Color(ElementPainter.CANVAS.getRGB());
            Color dot = new Color(GridLayer.DOT.getRGB());
            assertEquals("no grid by default", background, pixel(first, 100, 100));

            apply(new CanvasOptions(true, true, 1.0));

            for (DiagramDriver d : List.of(first, second)) {
                assertEquals("a dot at a grid point", dot, pixel(d, 100, 100));
                assertEquals("nothing between grid points", background, pixel(d, 105, 105));
                assertUntouched(d, DIAGRAM);
            }
        }
    }

    @Test
    public void movingSnapsToTheSpacingOnlyWithSnappingOn() {
        try (var d = open("snap.adpsample")) {
            apply(new CanvasOptions(false, false, 1.0));
            assertUntouched(d, DIAGRAM);
            d.moveBy(7, 3, "a");
            assertTrue(d.driver().text(), d.driver().text().contains("x=\"207\" y=\"203\""));

            d.driver().undo();
            apply(new CanvasOptions(false, true, 1.0));
            d.moveBy(7, 3, "a");
            assertTrue(d.driver().text(), d.driver().text().contains("x=\"210\" y=\"200\""));
        }
    }

    @Test
    public void theOpeningZoomAppliesUnlessTheFileRemembersItsOwn() {
        apply(new CanvasOptions(false, true, 1.5));
        try (var d = open("zoom.adpsample")) {
            assertEquals(1.5, d.zoomLevel(), 1e-9);
            d.tool().setState(new ToolState(0.75, List.of()));
            assertEquals("the remembered zoom wins", 0.75, d.zoomLevel(), 1e-9);
            assertUntouched(d, DIAGRAM);
        }
    }

    @Test
    public void aToolThatFixesAnOptionIgnoresTheUsers() {
        FileEditorProvider.EP_FILE_EDITOR_PROVIDER.getPoint().registerExtension(new SnapFixed(), getTestRootDisposable());
        apply(new CanvasOptions(false, false, 1.0));
        try (var d = open("fixed.adpfixed")) {
            assertEquals("etalii.adp.snapfixed", d.driver().editorTypeIdUsed());
            d.moveBy(7, 3, "a");
            assertTrue(d.driver().text(), d.driver().text().contains("x=\"210\" y=\"200\""));
        }
    }
}
