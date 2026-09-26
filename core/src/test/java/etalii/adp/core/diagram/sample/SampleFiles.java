package etalii.adp.core.diagram.sample;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/** The hand-written sample files, and generated large ones for the performance tests (SC-005). */
public final class SampleFiles {

    private SampleFiles() {
    }

    /** {@code core/testdata/sample}. */
    public static Path directory() {
        return Path.of(System.getProperty("adp.testdata", "testdata")).resolve("sample").toAbsolutePath();
    }

    /** A sample file's text, byte for byte. */
    public static String read(String name) {
        try {
            return Files.readString(directory().resolve(name), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * A laid-out grid of tasks joined by flows, the same for the same seed. Most flows join grid
     * neighbours, as in real diagrams, so most routes are clear.
     */
    public static String generate(int elements, int connections, long seed) {
        Random random = new Random(seed);
        int columns = (int) Math.ceil(Math.sqrt(elements));
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<sample>\n");
        for (int i = 0; i < elements; i++) {
            xml.append("  <box id=\"t").append(i).append("\" type=\"task\" x=\"").append(40 + i % columns * 200).append("\" y=\"")
                    .append(40 + i / columns * 120).append("\" w=\"120\" h=\"60\">Task ").append(i).append("</box>\n");
        }
        for (int j = 0; j < connections; j++) {
            int from = random.nextInt(elements);
            int to = switch (random.nextInt(3)) {
            case 0 -> from % columns + 1 < columns && from + 1 < elements ? from + 1 : random.nextInt(elements);
            case 1 -> from + columns < elements ? from + columns : random.nextInt(elements);
            default -> random.nextInt(elements);
            };
            if (to == from) {
                to = (from + 1) % elements;
            }
            xml.append("  <link id=\"f").append(j).append("\" type=\"flow\" from=\"t").append(from).append("\" fromAnchor=\"out\" to=\"t")
                    .append(to).append("\" toAnchor=\"in\"/>\n");
        }
        return xml.append("</sample>\n").toString();
    }
}