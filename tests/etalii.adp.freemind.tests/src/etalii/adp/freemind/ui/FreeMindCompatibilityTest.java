package etalii.adp.freemind.ui;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.freemind.edit.MindMapEdits;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.parse.MindMapParser;
import etalii.adp.testing.DesignerDriver;

/**
 * FR-012 and SC-005, opt-in (research R10). FreeMind is GPL, so it cannot be a build dependency.
 * When {@code FREEMIND_HOME} points at a FreeMind 1.0.1 installation, each example is edited and
 * saved, then read in a separate JVM by FreeMind's own XML reader ({@code freemind.main.XMLElement}
 * from {@code lib/freemind.jar}), and the node tree and text it reads must match what the designer
 * showed. Without {@code FREEMIND_HOME} the test is skipped.
 */
class FreeMindCompatibilityTest {

    /** Prints one line per node, depth then TEXT (or {@code <rich>}), in document order. */
    private static final String DUMP = """
            import freemind.main.XMLElement;
            import java.io.*;

            public class FreeMindDump {
                public static void main(String[] args) throws Exception {
                    XMLElement map = new XMLElement();
                    try (Reader in = new InputStreamReader(new FileInputStream(args[0]), "UTF-8")) {
                        map.parseFromReader(in);
                    }
                    dump(map, 0);
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

    @ParameterizedTest
    @MethodSource("etalii.adp.freemind.MindMapAsserts#examples")
    void freeMindReadsWhatTheDesignerSaved(Path example) throws Exception {
        String home = System.getenv("FREEMIND_HOME");
        assumeTrue(home != null && !home.isBlank(), "FREEMIND_HOME is not set");
        Path jar = Path.of(home, "lib", "freemind.jar");
        work = Files.createTempDirectory("freemind-check");

        try (var d = DesignerDriver.open(example, MindMapEditor.ID)) {
            MindMap map = (MindMap) d.editor().model();
            MapNode first = map.root().children().isEmpty() ? map.root() : map.root().children().get(0);
            apply(d, MindMapEdits.addChild(map, map.root().key(), "Added \u00e9 & <more>"));
            apply(d, MindMapEdits.rename((MindMap) d.editor().model(), first.key(), "Renamed"));

            Path saved = work.resolve(example.getFileName());
            Files.write(saved, d.savedBytes());

            assertEquals(designerTree((MindMap) d.editor().model()), freeMindTree(jar, saved), example.toString());
        }
    }

    private static void apply(DesignerDriver d, Edit edit) {
        d.editor().execute(edit.label(), edit.textEdit());
    }

    private static List<String> designerTree(MindMap map) throws Exception {
        List<String> lines = new ArrayList<>();
        collect(MindMapParser.parse(map.text()).root(), 0, lines);
        return lines;
    }

    private static void collect(MapNode node, int depth, List<String> lines) {
        String text = node.ranges().attribute("TEXT") == null ? "<rich>" : node.text().replace("\n", "\\n");
        lines.add(depth + "\t" + text);
        for (MapNode child : node.children()) {
            collect(child, depth + 1, lines);
        }
    }

    private List<String> freeMindTree(Path jar, Path map) throws IOException, InterruptedException {
        Path source = work.resolve("FreeMindDump.java");
        Files.writeString(source, DUMP);
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process process = new ProcessBuilder(java, "-Dfile.encoding=UTF-8", "-cp", jar.toString(), source.toString(), map.toString())
                .redirectErrorStream(true).start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new AssertionError("FreeMind's reader failed on " + map + ":\n" + new String(output, UTF_8));
        }
        return new String(output, UTF_8).lines().toList();
    }
}
