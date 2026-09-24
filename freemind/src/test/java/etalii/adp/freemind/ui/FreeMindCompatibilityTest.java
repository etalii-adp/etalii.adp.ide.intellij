package etalii.adp.freemind.ui;

import static etalii.adp.freemind.ui.AddNodeTest.ADD_CHILD;
import static etalii.adp.freemind.ui.RenameTest.RENAME;
import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.TestDialogManager;
import com.intellij.testFramework.FileEditorManagerTestCase;

import etalii.adp.freemind.FreeMindAsserts;
import etalii.adp.freemind.edit.FreeMindConventions;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.parse.MindMapParser;
import etalii.adp.testing.DesignerDriver;

/**
 * Spec 001 FR-012, SC-005, opt-in (research R10). FreeMind is GPL, so it cannot be a build
 * dependency. When {@code FREEMIND_HOME} points at a FreeMind 1.0.1 installation, each example is
 * edited in the designer and saved, and every reference result and a new map's text are read in a
 * separate JVM by FreeMind's own XML reader ({@code freemind.main.XMLElement} from
 * {@code lib/freemind.jar}); the node tree and text it reads must match the designer's. Without
 * {@code FREEMIND_HOME} the test is skipped with that reason.
 */
@RunWith(JUnit4.class)
public class FreeMindCompatibilityTest extends FileEditorManagerTestCase {

    /** Prints one line per node, depth then TEXT (or {@code <rich>}), in document order. */
    private static final String DUMP = """
            import freemind.main.XMLElement;
            import java.io.*;

            public class FreeMindDump {
                public static void main(String[] args) throws Exception {
                    for (String file : args) {
                        XMLElement map = new XMLElement();
                        try (Reader in = new InputStreamReader(new FileInputStream(file), "UTF-8")) {
                            map.parseFromReader(in);
                        }
                        System.out.println("# " + new File(file).getName());
                        dump(map, 0);
                    }
                }

                static void dump(XMLElement parent, int depth) {
                    for (Object child : parent.getChildren()) {
                        XMLElement element = (XMLElement) child;
                        if ("node".equals(element.getName())) {
                            String text = element.getStringAttribute("TEXT");
                            System.out.println(depth + "\\t" + (text == null ? "<rich>" : text.replace("\\n", "\\\\n")));
                            dump(element, depth + 1);
                        }
                    }
                }
            }
            """;

    private Path work;

    @Override
    public void setUp() {
        super.setUp();
    }

    @Test
    public void freeMindReadsWhatTheDesignerSaved() throws Exception {
        String home = System.getenv("FREEMIND_HOME");
        Assume.assumeTrue("FREEMIND_HOME is not set, so FreeMind 1.0.1's reader is not available", home != null && !home.isBlank());
        Path jar = Path.of(home, "lib", "freemind.jar");
        work = Files.createTempDirectory("freemind-check");
        TestDialogManager.setTestDialog(message -> Messages.OK, getTestRootDisposable());

        List<Path> files = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        for (Path example : FreeMindAsserts.examples()) {
            try (var d = DesignerDriver.open(myFixture, example)) {
                MindMapDesigner designer = LayoutTest.designer(d);
                MindMap map = designer.model();
                d.select(map.root().key()).run(ADD_CHILD).typeInPlace("Added é & <more>");
                MapNode first = map.root().children().isEmpty() ? map.root() : map.root().children().get(0);
                d.select(first.key()).run(RENAME);
                if (d.inPlaceField() != null) {
                    d.typeInPlace("Renamed");
                }
                Path saved = work.resolve(example.getFileName());
                Files.write(saved, d.savedBytes());
                files.add(saved);
                expected.addAll(tree(saved));
            }
        }
        try (Stream<Path> maps = Files.list(FreeMindAsserts.referenceDirectory())) {
            for (Path folder : maps.filter(Files::isDirectory).sorted().toList()) {
                try (Stream<Path> results = Files.list(folder)) {
                    for (Path result : results.filter(p -> p.toString().endsWith(".mm")).sorted().toList()) {
                        Path copy = work.resolve(folder.getFileName() + "-" + result.getFileName());
                        Files.copy(result, copy);
                        files.add(copy);
                        expected.addAll(tree(copy));
                    }
                }
            }
        }
        Path newMap = work.resolve("new-map.mm");
        Files.writeString(newMap, FreeMindConventions.newMapText("ID_1", FreeMindConventions.now(), "\n"), UTF_8);
        files.add(newMap);
        expected.addAll(tree(newMap));

        assertEquals(expected, freeMindTree(jar, files));
    }

    /** The node tree as the designer's parser reads the file, in the dump's form. */
    private static List<String> tree(Path file) throws Exception {
        List<String> lines = new ArrayList<>();
        lines.add("# " + file.getFileName());
        String text = Files.readString(file, UTF_8).replace("\r\n", "\n").replace('\r', '\n');
        collect(MindMapParser.parse(text).root(), 0, lines);
        return lines;
    }

    private static void collect(MapNode node, int depth, List<String> lines) {
        String text = node.ranges().attribute("TEXT") == null ? "<rich>" : node.text().replace("\n", "\\n");
        lines.add(depth + "\t" + text);
        for (MapNode child : node.children()) {
            collect(child, depth + 1, lines);
        }
    }

    private List<String> freeMindTree(Path jar, List<Path> maps) throws IOException, InterruptedException {
        Path source = work.resolve("FreeMindDump.java");
        Files.writeString(source, DUMP);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>(List.of(java, "-Dfile.encoding=UTF-8", "-cp", jar.toString(), source.toString()));
        maps.forEach(map -> command.add(map.toString()));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(120, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new AssertionError("FreeMind's reader failed:\n" + new String(output, UTF_8));
        }
        return new String(output, UTF_8).lines().toList();
    }
}
