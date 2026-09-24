package etalii.adp.freemind;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Every spec 001 test has a named successor (FR-018, SC-007): each row of
 * {@code spec001-test-inventory.md} names a test class and method that exist in this build.
 */
class InventoryCoverageTest {

    private static final Pattern ROW = Pattern.compile("^\\| `([\\w.]+)` \\|.*\\| `(\\w+)\\.(\\w+)` \\|\\s*$");

    @Test
    void everyInventoriedTestHasItsSuccessor() throws IOException {
        List<String> rows = Files.readAllLines(FreeMindAsserts.referenceDirectory().resolve("spec001-test-inventory.md"), UTF_8);
        Map<String, Path> testClasses = testClasses();
        List<String> missing = new ArrayList<>();
        int checked = 0;
        for (String row : rows) {
            Matcher match = ROW.matcher(row);
            if (!match.matches()) {
                continue;
            }
            checked++;
            Path source = testClasses.get(match.group(2));
            if (source == null) {
                missing.add(match.group(1) + " -> " + match.group(2) + " (no such test class)");
            } else if (!Pattern.compile("\\bvoid " + match.group(3) + "\\s*\\(").matcher(Files.readString(source, UTF_8)).find()) {
                missing.add(match.group(1) + " -> " + match.group(2) + "." + match.group(3) + " (no such test method)");
            }
        }
        assertTrue(checked > 100, "Only " + checked + " inventory rows were read");
        assertEquals(List.of(), missing);
    }

    /** Test sources of every module, by simple class name. */
    private static Map<String, Path> testClasses() throws IOException {
        Path modules = Path.of("").toAbsolutePath().getParent();
        try (Stream<Path> files = Stream.of("core", "freemind").map(m -> modules.resolve(m).resolve("src/test/java")).filter(Files::isDirectory)
                .flatMap(InventoryCoverageTest::walk)) {
            return files.filter(p -> p.toString().endsWith(".java"))
                    .collect(Collectors.toMap(p -> p.getFileName().toString().replace(".java", ""), Function.identity(), (a, b) -> a));
        }
    }

    private static Stream<Path> walk(Path directory) {
        try {
            return Files.walk(directory).toList().stream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
