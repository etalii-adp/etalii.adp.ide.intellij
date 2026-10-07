package etalii.adp.fbl.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;

/** A folder of its own for one test, removed when the test closes it. */
public final class TemporaryFolder implements AutoCloseable {

    private final Path root;

    public TemporaryFolder() {
        try {
            // The real path: on macOS and on Windows the temporary folder is itself reached through a link or a short name.
            root = Files.createTempDirectory("adp-fbl-").toRealPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path root() {
        return root;
    }

    /** Writes a file, creating its folders, and returns its path. */
    public Path write(String relative, byte[] content) {
        Path file = root.resolve(relative);
        try {
            Files.createDirectories(file.getParent());
            return Files.write(file, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public Path folder(String relative) {
        try {
            return Files.createDirectories(root.resolve(relative));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Creates a symbolic link. Where the system refuses, as Windows does without the privilege,
     * the test is aborted with the system's reason, so the skip is reported and not passed over.
     */
    public Path link(String relative, Path target) {
        Path link = root.resolve(relative);
        try {
            Files.createDirectories(link.getParent());
            return Files.createSymbolicLink(link, target);
        } catch (IOException | UnsupportedOperationException | SecurityException e) {
            Assumptions.abort("This system refuses a symbolic link: " + e);
            throw new AssertionError(e);
        }
    }

    @Override
    public void close() {
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.sorted(Comparator.reverseOrder()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Path path : paths) {
            try {
                Files.deleteIfExists(path);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
