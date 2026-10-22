package etalii.adp.fbl.conformance;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.fbl.support.Corpus;

/**
 * The conformance corpus is the copy it was made as (spec 010, FR-015): every file has the digest
 * {@code SHA256SUMS} lists for it. A fixture states byte offsets, so a copy whose line endings
 * were converted, or that was edited here, no longer means what FBL published. The same holds of the
 * files of FBL 0.8 copied on their own, with a list of their own (etalii.adp spec 033).
 */
class CorpusUnchangedTest {

    private static final String LIST = "SHA256SUMS";

    static List<Path> corpora() {
        return Corpus.conformanceCorpora();
    }

    @ParameterizedTest
    @MethodSource("corpora")
    void everyCopiedFileHasItsListedDigest(Path corpus) throws NoSuchAlgorithmException {
        Map<String, String> listed = new TreeMap<>();
        for (String line : new String(Corpus.bytes(corpus.resolve(LIST)), UTF_8).split("\n")) {
            if (!line.isBlank()) {
                listed.put(line.substring(66).strip(), line.substring(0, 64));
            }
        }
        List<String> files = Corpus.files(corpus).stream().filter(f -> !f.equals(LIST)).toList();
        assertFalse(files.isEmpty(), "No file under " + corpus);

        List<String> problems = new ArrayList<>();
        for (String file : files) {
            String expected = listed.remove(file);
            String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Corpus.bytes(corpus.resolve(file))));
            if (expected == null) {
                problems.add(file + " is not listed");
            } else if (!expected.equals(actual)) {
                problems.add(file + " differs from the copy that was made");
            }
        }
        listed.keySet().forEach(file -> problems.add(file + " is listed and missing"));
        assertEquals(List.of(), problems);
    }
}
