package etalii.adp.freemind;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The plug-in makes no network access at runtime (FR-021): no main source in core or freemind opens
 * a connection or socket. Handing a link to the user's browser in {@code LinkOpener} is the only
 * outward call, and it happens on an explicit click.
 */
class NoNetworkAccessTest {

    private static final Pattern NETWORK = Pattern.compile(
            "java\\.net\\.(URLConnection|HttpURLConnection|Socket|ServerSocket|DatagramSocket|http\\.)|\\bHttpClient\\b|\\bopenConnection\\s*\\(|\\bopenStream\\s*\\(|HttpRequests");

    @Test
    void noMainSourceOpensAConnection() throws IOException {
        Path modules = Path.of("").toAbsolutePath().getParent();
        List<String> hits;
        try (Stream<Path> files = Stream.of("core", "freemind").map(m -> modules.resolve(m).resolve("src/main/java")).flatMap(NoNetworkAccessTest::walk)) {
            hits = files.filter(p -> p.toString().endsWith(".java")).filter(p -> NETWORK.matcher(read(p)).find())
                    .map(p -> modules.relativize(p).toString()).toList();
        }
        assertEquals(List.of(), hits);
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
