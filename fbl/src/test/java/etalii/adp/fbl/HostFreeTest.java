package etalii.adp.fbl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;

import etalii.adp.fbl.support.Corpus;

/**
 * The FBL library is generic and needs no host (spec 010, FR-001 to FR-003): its main sources
 * import nothing from the platform, AWT, Swing or the network, and name none of the example
 * bindings. Ids and anything else a binding of the corpus needs come from the caller.
 */
class HostFreeTest {

    private static final List<String> FORBIDDEN = List.of("import com.intellij.", "import java.awt.", "import javax.swing.",
            "import java.net.", "import java.nio.channels.");

    private static final Path MAIN = Path.of("src/main/java");

    @Test
    void theLibraryImportsNoHostAndNoNetwork() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (String file : sources()) {
            for (String line : Files.readAllLines(MAIN.resolve(file), UTF_8)) {
                if (FORBIDDEN.stream().anyMatch(line.strip()::startsWith)) {
                    offenders.add(file + ": " + line.strip());
                }
            }
        }
        assertEquals(List.of(), offenders);
    }

    @Test
    void theLibraryNamesNoExampleBinding() throws IOException {
        List<String> names = Corpus.files(Corpus.conformance()).stream().filter(f -> f.endsWith(".fbl") && !f.contains("/"))
                .map(f -> f.substring(0, f.length() - ".fbl".length())).toList();
        assertFalse(names.isEmpty(), "No binding under " + Corpus.conformance());
        List<String> offenders = new ArrayList<>();
        for (String file : sources()) {
            String text = Files.readString(MAIN.resolve(file), UTF_8).toLowerCase(Locale.ROOT);
            names.stream().filter(text::contains).forEach(name -> offenders.add(file + " names " + name));
        }
        assertEquals(List.of(), offenders);
    }

    private static List<String> sources() {
        List<String> sources = Corpus.files(MAIN).stream().filter(f -> f.endsWith(".java")).toList();
        assertFalse(sources.isEmpty(), "No source under " + MAIN.toAbsolutePath());
        return sources;
    }
}
