package etalii.adp.core.diagram;

import java.awt.Rectangle;
import java.awt.geom.Rectangle2D;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.model.End;
import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.core.diagram.view.ConnectionView;
import etalii.adp.core.diagram.view.ElementView;
import etalii.adp.testing.DiagramDriver;
import etalii.adp.testing.Layout;

/** US1-4 and the edge cases: unreadable files, unknown types, and a definition that does not hold together. */
@RunWith(JUnit4.class)
public class PlaceholderAndProblemTest extends FileEditorManagerTestCase {

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable());
    }

    private DiagramDriver open(String name) {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve(name));
    }

    @Test
    public void us1_4_aFileTheMappingCannotReadOpensInTheTextViewWithTheProblem() {
        try (var d = open("broken.adpsample")) {
            assertTrue(d.driver().problemShown());
            assertEquals(Layout.TEXT, d.driver().layout());
            assertNull(d.tool().diagram());
            assertEquals(List.of(), d.elementKeys());
            assertEquals(SampleFiles.read("broken.adpsample"), d.driver().text());
            assertFalse(d.driver().isModified());
        }
    }

    @Test
    public void unknownTypesAreDrawnAsPlaceholders() {
        try (var d = open("unknown-type.adpsample")) {
            ElementView gizmo = d.elementView("z");
            assertTrue(gizmo.placeholder());
            assertEquals("gizmo", gizmo.type());
            assertTrue(gizmo.texts().containsValue("gizmo"));
            assertEquals(new Rectangle(240, 40, 80, 40), gizmo.bounds());
            assertSame(Outline.RECTANGLE, gizmo.outline());
            assertEquals(List.of(), d.anchorsOf("z"));

            ConnectionView wire = d.connectionView("w1");
            assertTrue(wire.placeholder());
            assertEquals("wire", wire.type());
            assertEquals(Dash.DASHED, wire.dash());
            assertEquals(LineStyle.STRAIGHT, wire.line());
            assertEquals(ArrowHead.NONE, wire.target());

            ConnectionView flow = d.connectionView("f1");
            assertFalse("a known connection to a placeholder is drawn as declared", flow.placeholder());
            assertNotNull(flow.route());
            assertFalse(d.elementView("a").placeholder());
        }
    }

    @Test
    public void placeholdersAreSelectable() {
        try (var d = open("unknown-type.adpsample")) {
            d.driver().select("z");
            assertEquals(List.of("z"), d.driver().selectedKeys());

            d.driver().run("etalii.adp.core.SelectAll");
            assertTrue(d.driver().selectedKeys().containsAll(List.of("a", "z", "f1", "w1")));
        }
    }

    @Test
    public void placeholdersAreNeverMovedEditedOrConnectedAndAreKept() {
        String original = SampleFiles.read("unknown-type.adpsample");
        try (var d = open("unknown-type.adpsample")) {
            var commands = d.tool().commands();

            assertFalse(commands.move(Map.of("z", new Rectangle2D.Double(300, 300, 80, 40))).allowed());
            assertEquals("unknown type is kept as it is", d.refusal());
            assertFalse(commands.setProperty(List.of("z"), "title", "Changed").allowed());
            assertFalse(commands.connect("flow", new End("a", "out"), new End("z", null)).allowed());
            assertFalse(commands.remove(List.of("w1")).allowed());
            assertEquals(original, d.driver().text());

            assertTrue(commands.setProperty(List.of("a"), "title", "Known!").allowed());
            assertNull(d.refusal());
            assertEquals(original.replace(">Known<", ">Known!<"), d.driver().text());
            assertTrue(d.elementView("z").placeholder());
            assertTrue(d.connectionView("w1").placeholder());
        }
    }

    @Test
    public void aDefinitionThatDoesNotHoldTogetherFailsTheProvidersConstruction() {
        var broken = SampleDefinition.builder().element("extra", e -> e.anchor("x", a -> a.accepts("wire", Direction.IN)));

        DefinitionException problem = org.junit.Assert.assertThrows(DefinitionException.class, () -> new SampleProvider(broken));

        assertTrue(problem.getMessage(), problem.problems().contains("element 'extra' > anchor 'x': names undeclared connection type 'wire'"));
    }
}
