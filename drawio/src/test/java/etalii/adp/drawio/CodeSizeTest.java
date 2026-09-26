package etalii.adp.drawio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * T104, SC-001 (research R22): a designer built on the framework needs at most a tenth of the
 * FreeMind designer's code, and none of its own drawing, selection, undo or property panel code.
 * Code lines are counted: blank lines, comment lines and the package and import lines are left out.
 */
class CodeSizeTest {

    /** Non-blank, non-comment lines of FreeMind's main sources before the migration, on 2026-09-25. */
    static final int FREEMIND_BASELINE = 3423;

    static final int LIMIT = FREEMIND_BASELINE / 10;

    private static final Pattern FRAMEWORK_CODE = Pattern.compile("\\b(Graphics2D|UndoManager|JTable|MouseListener)\\b");

    private static final Path MODULES = Path.of("").toAbsolutePath().getParent();

    private static final List<Path> SAMPLE = Stream.of("SampleDefinition", "SampleRules", "SampleMapping", "SampleProvider")
            .map(name -> MODULES.resolve("core/src/test/java/etalii/adp/core/diagram/sample/" + name + ".java")).toList();

    @Test
    void theDrawioDesignerIsATenthOfFreeMind() {
        List<Path> sources = javaFiles(MODULES.resolve("drawio/src/main/java"));
        assertTrue(sources.size() >= 5, sources.toString());
        int lines = count(sources);
        System.out.println("SC-001: drawio/src/main/java has " + lines + " lines; the limit is " + LIMIT);
        assertTrue(lines <= LIMIT, "drawio has " + lines + " lines, more than " + LIMIT);
    }

    @Test
    void theSampleDesignerIsATenthOfFreeMind() {
        SAMPLE.forEach(file -> assertTrue(Files.isRegularFile(file), file.toString()));
        int lines = count(SAMPLE);
        System.out.println("SC-001: the sample designer's main parts have " + lines + " lines; the limit is " + LIMIT);
        assertTrue(lines <= LIMIT, "the sample designer has " + lines + " lines, more than " + LIMIT);
    }

    @Test
    void neitherDrawsSelectsUndoesNorShowsProperties() {
        List<Path> sources = Stream.concat(javaFiles(MODULES.resolve("drawio/src/main/java")).stream(), SAMPLE.stream()).toList();
        List<String> hits = sources.stream().filter(file -> FRAMEWORK_CODE.matcher(read(file)).find()).map(file -> MODULES.relativize(file).toString())
                .toList();
        assertEquals(List.of(), hits);
    }

    @Test
    void commentsBlankLinesAndImportsDoNotCount() {
        assertEquals(2, countLines("""
                package a;

                import java.util.List;

                /** A comment
                 * over lines.
                 */
                // one more
                class A {
                }
                """));
    }

    static int count(List<Path> files) {
        return files.stream().mapToInt(file -> countLines(read(file))).sum();
    }

    static int countLines(String source) {
        return (int) source.lines().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("//") && !line.startsWith("/*") && !line.startsWith("*")
                        && !line.startsWith("package ") && !line.startsWith("import "))
                .count();
    }

    private static List<Path> javaFiles(Path directory) {
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(file -> file.toString().endsWith(".java")).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
