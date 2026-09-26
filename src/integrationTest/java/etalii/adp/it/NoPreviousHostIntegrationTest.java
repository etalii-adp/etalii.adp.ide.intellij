package etalii.adp.it;

import static java.nio.charset.StandardCharsets.ISO_8859_1;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;

/**
 * Nothing refers to the previous host (FR-017, SC-005): not the tracked files outside this
 * feature's own spec, not the plug-in zip's entries or their contents, and not the plug-in as the
 * sandbox IDE installed it. The search ignores case.
 */
class NoPreviousHostIntegrationTest {

    /** The previous host's name, spelled so this file does not match itself. */
    private static final String NAME = "ecl" + "ipse";

    private static final String OWN_SPEC = "specs/002-jetbrains-ide-support/";

    /** Spec 003's library survey names third-party libraries that carry the word, not the host. */
    private static final String LIBRARY_SURVEY = "specs/003-diagram-designer-framework/library-survey.md";

    private final Path repository = Path.of(System.getProperty("adp.repository", "."));

    @Test
    void theTrackedFilesDoNotNameIt() throws Exception {
        List<String> hits = new ArrayList<>();
        for (String file : trackedFiles()) {
            if (file.startsWith(OWN_SPEC) || file.equals(LIBRARY_SURVEY)) {
                continue;
            }
            if (mentions(file.getBytes(UTF_8)) || Files.isRegularFile(repository.resolve(file)) && mentions(Files.readAllBytes(repository.resolve(file)))) {
                hits.add(file);
            }
        }
        assertEquals(List.of(), hits);
    }

    @Test
    void thePluginZipDoesNotNameIt() throws Exception {
        Path zip = Path.of(System.getProperty("adp.plugin.zip"));
        assertTrue(Files.isRegularFile(zip), "No plug-in zip at " + zip);
        List<String> hits = new ArrayList<>();
        try (InputStream in = Files.newInputStream(zip)) {
            scanArchive(zip.getFileName().toString(), in, hits);
        }
        assertEquals(List.of(), hits);
    }

    @Test
    void theInstalledPluginDoesNotNameIt() throws Exception {
        Path sandbox = repository.resolve(".intellijPlatform/sandbox");
        List<Path> installed;
        try (Stream<Path> paths = Files.exists(sandbox) ? Files.walk(sandbox, 4) : Stream.empty()) {
            installed = paths.filter(p -> p.getFileName().toString().equals("EtAlii.Adp.IntelliJ") && p.getParent().getFileName().toString().startsWith("plugins"))
                    .toList();
        }
        List<String> hits = new ArrayList<>();
        for (Path plugin : installed) {
            try (Stream<Path> files = Files.walk(plugin)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    try (InputStream in = Files.newInputStream(file)) {
                        if (file.toString().endsWith(".jar")) {
                            scanArchive(plugin.relativize(file).toString(), in, hits);
                        } else if (mentions(in.readAllBytes()) || mentions(file.toString().getBytes(UTF_8))) {
                            hits.add(file.toString());
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), hits);
    }

    private List<String> trackedFiles() throws IOException, InterruptedException {
        Process git = new ProcessBuilder("git", "ls-files", "-z").directory(repository.toFile()).redirectErrorStream(true).start();
        String out = new String(git.getInputStream().readAllBytes(), UTF_8);
        assertEquals(0, git.waitFor(), out);
        return List.of(out.split("\0")).stream().filter(s -> !s.isBlank()).toList();
    }

    /** Entry names and contents, descending into nested archives. */
    private static void scanArchive(String name, InputStream archive, List<String> hits) throws IOException {
        ZipInputStream zip = new ZipInputStream(archive);
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            String path = name + "!/" + entry.getName();
            byte[] content = zip.readAllBytes();
            if (mentions(entry.getName().getBytes(UTF_8)) || mentions(content)) {
                hits.add(path);
            }
            if (entry.getName().endsWith(".jar") || entry.getName().endsWith(".zip")) {
                scanArchive(path, new ByteArrayInputStream(content), hits);
            }
        }
    }

    private static boolean mentions(byte[] content) {
        return new String(content, ISO_8859_1).toLowerCase(Locale.ROOT).contains(NAME);
    }
}
