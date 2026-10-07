package etalii.adp.fbl.real;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiPredicate;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.support.Corpus;
import etalii.adp.fbl.support.CorpusBindings;
import etalii.adp.fbl.support.NaturalIds;

/**
 * The real files the copied bindings are tried on (spec 010, FR-019 to FR-021): the files of
 * standalone under {@code fbl/testdata/real/}, by the paths they have there, and this repository's
 * own mind maps under {@code freemind/testdata}. Both are enumerated at run time, so a file added
 * later is covered without a change here, and every enumeration asserts the count recorded when
 * the suite was written, so a broken enumeration fails rather than passing on no files. The
 * counterpart of standalone's {@code RealFiles/RealFileCorpus.cs}.
 */
final class RealFileCorpus {

    /** The prefix of a mind map's path, which no path under {@code fbl/testdata/real/} starts with. */
    static final String MAPS = "freemind/testdata/";

    /** Registrations: every {@code .adp} under {@code fbl/testdata/real/}. */
    static final int MINIMUM_REGISTRATIONS = 32;

    /** The declared file bindings and the files each takes, with the counts recorded when the suite was written. */
    static final List<CorpusBinding> DECLARED = List.of(
            new CorpusBinding("timeline", "timeline.fbl", "timeline", 15, false, (path, bytes) -> extension(path, ".tml")),
            new CorpusBinding("causal-loop", "causal-loop-diagram.fbl", "cld", 4, false, (path, bytes) -> extension(path, ".cld")),
            new CorpusBinding("mindmap", "mindmap.fbl", "freeplane", 108, true, (path, bytes) -> extension(path, ".mm")),
            new CorpusBinding("structurizr", "structurizr.fbl", "workspace", 16, false, (path, bytes) -> extension(path, ".dsl")),
            new CorpusBinding("databricks-job", "databricks-job.fbl", "job", 4, false,
                    (path, bytes) -> (extension(path, ".yml") || extension(path, ".yaml")) && contains(bytes, "task_key")),
            new CorpusBinding("databricks-pipeline", "databricks-pipeline.fbl", "settings", 2, false,
                    (path, bytes) -> extension(path, ".json") && contains(bytes, "\"libraries\"")));

    private RealFileCorpus() {
    }

    /**
     * One copied binding tried on the real files: which files it takes, and how many there were when the suite was written.
     *
     * @param maps true when its files are this repository's mind maps, false when they are the copied files
     */
    record CorpusBinding(String key, String document, String binding, int minimum, boolean maps, BiPredicate<String, byte[]> selects) {

        /** The binding as the divergence record names it: its document and its name. */
        String reference() {
            return document + "#" + binding;
        }

        @Override
        public String toString() {
            return key;
        }
    }

    /** The folder the copied files' paths are relative to, and the workspace root their registrations are opened in. */
    static Path real() {
        return Corpus.root().resolve("real").toAbsolutePath().normalize();
    }

    /** This repository's mind maps, from the system property the build sets. */
    static Path maps() {
        return Path.of(System.getProperty("adp.freemind.testdata", "../freemind/testdata")).toAbsolutePath().normalize();
    }

    /** Every copied file, relative to {@code fbl/testdata/real}, {@code /}-separated, in ordinal order. */
    static List<String> copied() {
        return Corpus.files(real());
    }

    /** Every file under the mind maps' folder, as {@code freemind/testdata/...}, in ordinal order. */
    static List<String> ownMaps() {
        return Corpus.files(maps()).stream().map(file -> MAPS + file).sorted().toList();
    }

    static Path fullPath(String relative) {
        return relative.startsWith(MAPS) ? maps().resolve(relative.substring(MAPS.length())) : real().resolve(relative);
    }

    static byte[] bytes(String relative) {
        return Corpus.bytes(fullPath(relative));
    }

    static CorpusBinding find(String key) {
        List<CorpusBinding> found = DECLARED.stream().filter(b -> b.key().equals(key)).toList();
        assertTrue(found.size() == 1, () -> "No declared binding, or more than one, has the key " + key);
        return found.get(0);
    }

    /** The files a declared binding takes, without the check of their count. */
    static List<String> select(CorpusBinding binding) {
        return (binding.maps() ? ownMaps() : copied()).stream().filter(path -> binding.selects().test(path, head(path))).toList();
    }

    /** The files a declared binding takes; fails when fewer are found than when the suite was written. */
    static List<String> files(CorpusBinding binding) {
        List<String> files = select(binding);
        assertTrue(files.size() >= binding.minimum(), () -> binding.key() + ": " + files.size() + " files were found; at least " + binding.minimum()
                + " were there when this suite was written.");
        return files;
    }

    /** The registrations, without the check of their count. */
    static List<String> selectRegistrations() {
        return copied().stream().filter(file -> extension(file, ".adp")).toList();
    }

    /** Every registration; fails when fewer are found than when the suite was written. */
    static List<String> registrations() {
        List<String> files = selectRegistrations();
        assertTrue(files.size() >= MINIMUM_REGISTRATIONS, () -> files.size() + " registrations were found; at least " + MINIMUM_REGISTRATIONS
                + " were there when this suite was written.");
        return files;
    }

    /** The {@code (binding, file)} pairs of every declared binding, for a parameterized test. */
    static Stream<Arguments> pairs() {
        List<Arguments> data = new ArrayList<>();
        for (CorpusBinding binding : DECLARED) {
            for (String file : files(binding)) {
                data.add(Arguments.of(binding.key(), file));
            }
        }
        return data.stream();
    }

    static Stream<String> keys() {
        return DECLARED.stream().map(CorpusBinding::key);
    }

    static FblBinding binding(CorpusBinding binding) {
        return CorpusBindings.binding(binding.document(), binding.binding());
    }

    /** The options a real file is read with: its path for findings and the natural ids the fixtures use. */
    static FblOptions options(FblBinding binding, String relative) {
        return options(binding, relative, null);
    }

    /**
     * @param headers the registration's headers, or null when the body is opened without one
     */
    static FblOptions options(FblBinding binding, String relative, Map<String, String> headers) {
        return FblOptions.DEFAULT.withFileName(relative).withDeriveId(NaturalIds.forBinding(binding.name()))
                .withRegistrationHeaders(headers == null ? Map.of() : headers).withResource(headers == null ? null : headers.get("resource"));
    }

    private static boolean extension(String path, String extension) {
        return path.toLowerCase(Locale.ROOT).endsWith(extension);
    }

    private static boolean contains(byte[] bytes, String text) {
        return new String(bytes, UTF_8).contains(text);
    }

    /** The bytes a content selector looks at; only files whose extension could match are read. */
    private static byte[] head(String path) {
        return extension(path, ".yml") || extension(path, ".yaml") || extension(path, ".json") ? bytes(path) : new byte[0];
    }
}
