package etalii.adp.freemind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/** The format layer runs without the IDE: model, parse and edit import nothing from the platform, AWT or Swing. */
class FormatPurityTest {

    private static final List<String> FORBIDDEN = List.of("import com.intellij.", "import java.awt.", "import javax.swing.");

    @Test
    void theFormatLayerImportsNoHost() throws IOException {
        Path root = Path.of("src/main/java/etalii/adp/freemind");
        List<String> offenders;
        try (Stream<Path> files = Stream.of("model", "parse", "edit").map(root::resolve).flatMap(FormatPurityTest::javaFiles)) {
            offenders = files.flatMap(file -> lines(file).stream().filter(line -> FORBIDDEN.stream().anyMatch(line::startsWith))
                    .map(line -> root.relativize(file) + ": " + line)).toList();
        }
        assertEquals(List.of(), offenders);
    }

    private static Stream<Path> javaFiles(Path directory) {
        try {
            return Files.walk(directory).filter(p -> p.toString().endsWith(".java")).toList().stream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<String> lines(Path file) {
        try {
            return Files.readAllLines(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
