package etalii.adp.core.diagram;

import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.core.diagram.DiagramChange.Moved;
import etalii.adp.core.diagram.DiagramChange.PropertyChanged;
import etalii.adp.core.diagram.edit.DiagramCommands;
import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleProvider;
import etalii.adp.testing.DiagramDriver;

/** FR-004, R8: text edits, undo and changes on disk reach the diagram's listener as diagram changes. */
@RunWith(JUnit4.class)
public class ChangeNotificationTest extends FileEditorManagerTestCase {

    private final List<List<DiagramChange>> received = new ArrayList<>();
    private final List<DiagramCommands.Host> tools = new ArrayList<>();

    @Override
    public void setUp() {
        super.setUp();
        SampleProvider.register(getTestRootDisposable(), SampleDefinition.builder().listener((tool, changes) -> {
            tools.add(tool);
            received.add(changes);
        }));
    }

    private DiagramDriver open() {
        return DiagramDriver.open(myFixture, SampleFiles.directory().resolve("two-tasks.adpsample"));
    }

    @Test
    public void openingIsNotAChange() {
        try (var d = open()) {
            assertEquals(List.of(), received);
            assertEquals(List.of(), d.lastChanges());
        }
    }

    @Test
    public void aTextEditReachesTheListener() {
        try (var d = open()) {
            d.driver().editText(t -> t.replace(">Ship the goods", ">Send the goods"));

            List<DiagramChange> expected = List.of(new PropertyChanged("b", "title", "Ship the goods to the customer quickly",
                    "Send the goods to the customer quickly"));
            assertEquals(List.of(expected), received);
            assertSame(d.tool(), tools.get(0));
            assertEquals(expected, d.lastChanges());
        }
    }

    @Test
    public void undoReachesTheListener() {
        try (var d = open()) {
            d.driver().editText(t -> t.replace("owner=\"Ann\"", "owner=\"Bob\""));
            received.clear();

            d.driver().undo();

            assertEquals(SampleFiles.read("two-tasks.adpsample"), d.driver().text());
            assertEquals(List.of(List.of(new PropertyChanged("a", "owner", "Bob", "Ann"))), received);
        }
    }

    @Test
    public void aChangeOnDiskReachesTheListener() {
        try (var d = open()) {
            d.driver().changeOnDisk(SampleFiles.read("two-tasks.adpsample").replace("x=\"40\" y=\"40\" w=\"120\"", "x=\"50\" y=\"40\" w=\"120\""));

            assertEquals(List.of(List.of(new Moved("a", new Rectangle2D.Double(40, 40, 120, 60), new Rectangle2D.Double(50, 40, 120, 60)))),
                    received);
            assertEquals(50, d.elementView("a").bounds().x);
        }
    }

    @Test
    public void anUnreadableTextIsNotAChangeAndReadingAgainDiffsFromTheLastDiagram() {
        try (var d = open()) {
            String original = d.driver().text();
            d.driver().editText(t -> t.replace("</sample>", "<sample>"));
            assertTrue(d.driver().problemShown());
            assertEquals(List.of(), received);

            d.driver().editText(t -> original.replace(">submit<", ">send<"));
            assertEquals(List.of(List.of(new PropertyChanged("f1", "label", "submit", "send"))), received);
        }
    }
}
