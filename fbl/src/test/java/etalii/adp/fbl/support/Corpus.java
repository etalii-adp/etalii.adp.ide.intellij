package etalii.adp.fbl.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** The files under {@code fbl/testdata}, read as bytes: the conformance corpus, the real files and the lists beside them. */
public final class Corpus {

    private Corpus() {
    }

    /** {@code fbl/testdata}, from the system property the build sets. */
    public static Path root() {
        return Path.of(System.getProperty("adp.fbl.testdata", "testdata"));
    }

    /** The bindings, fixtures and registrations copied from {@code specifications/fbl/} in etalii.adp. */
    public static Path conformance() {
        return root().resolve("conformance");
    }

    /** The bytes of a file, by its path under {@code fbl/testdata} with {@code /} between the segments. */
    public static byte[] bytes(String relative) {
        return bytes(root().resolve(relative));
    }

    public static byte[] bytes(Path file) {
        try {
            return Files.readAllBytes(file);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Every regular file under a folder, as paths relative to it with {@code /} between the segments, in ordinal order. */
    public static List<String> files(Path folder) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(folder)) {
            return paths.filter(Files::isRegularFile).map(p -> folder.relativize(p).toString().replace('\\', '/'))
                    .sorted(Comparator.naturalOrder()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
