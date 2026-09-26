package etalii.adp.drawio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * T105, as in {@code freemind}: opening and editing draw.io files makes no network access (FR-021).
 * No main source in core or drawio opens a connection or socket, so neither the mapping nor the
 * designer can; draw.io files that point at images or libraries on the web are shown without them.
 */
class NoNetworkAccessTest {

    private static final Pattern NETWORK = Pattern.compile(
            "java\\.net\\.(URLConnection|HttpURLConnection|Socket|ServerSocket|DatagramSocket|http\\.)|\\bHttpClient\\b|\\bopenConnection\\s*\\(|\\bopenStream\\s*\\(|HttpRequests");

    @Test
    void noMainSourceOpensAConnection() throws IOException {
        Path modules = Path.of("").toAbsolutePath().getParent();
        List<Path> sources;
        try (Stream<Path> files = Stream.of("core", "drawio").map(m -> modules.resolve(m).resolve("src/main/java")).flatMap(NoNetworkAccessTest::walk)) {
            sources = files.filter(p -> p.toString().endsWith(".java")).toList();
        }
        assertTrue(sources.stream().anyMatch(p -> p.getFileName().toString().equals("DrawioMapping.java")), "the draw.io sources are scanned");
        assertEquals(List.of(), sources.stream().filter(p -> NETWORK.matcher(read(p)).find()).map(p -> modules.relativize(p).toString()).toList());
    }

    private static Stream<Path> walk(Path directory) {
        try {
            return Files.walk(directory).toList().stream();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
