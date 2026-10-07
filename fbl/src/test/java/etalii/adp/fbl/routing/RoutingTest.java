package etalii.adp.fbl.routing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.fbl.document.BodySettings;
import etalii.adp.fbl.document.Claims;
import etalii.adp.fbl.document.Family;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.Marker;
import etalii.adp.fbl.document.RegistrationSettings;
import etalii.adp.fbl.document.TextDefaults;
import etalii.adp.fbl.support.CorpusBindings;
import etalii.adp.fbl.support.TemporaryFolder;

/**
 * Markers (FBL §12.2), candidates (FBL §12.3), readings (FBL §9.4) and globs (FBL §10.1). The
 * counterparts of standalone's {@code Routing/Routing.Tests.cs}.
 */
class RoutingTest {

    private static final List<FblBinding> ALL = CorpusBindings.all().stream().map(CorpusBindings.Named::binding).toList();

    private static byte[] utf8(String text) {
        return text.getBytes(UTF_8);
    }

    /** A binding that claims an extension and says nothing else, read by the lines family. */
    private static FblBinding claiming(String name, String extension) {
        return new FblBinding(
                name,
                null,
                new Claims(List.of(extension), List.of(), false, false, null, List.of(), List.of(), Map.of()),
                new BodySettings(false, Family.LINES, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), BodySettings.DEFAULT_SETTLE),
                null,
                null,
                TextDefaults.DEFAULT,
                null,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                RegistrationSettings.DEFAULT,
                null);
    }

    static Stream<Arguments> patternBodies() {
        return Stream.of(
                Arguments.of("<map version=\"freeplane 1.11.5\">\n<node TEXT=\"a\"/>\n</map>\n", true),
                Arguments.of("\n\n\n\n<map>\n</map>\n", true),
                Arguments.of("\n\n\n\n\n<map>\n</map>\n", false),
                Arguments.of("<mapping/>\n", false));
    }

    @ParameterizedTest
    @MethodSource("patternBodies")
    void aPatternMarkerLooksAtItsFirstLines(String body, boolean matches) {
        // The mind map's marker, ^<map[\s>] within 5 lines.
        Marker marker = CorpusBindings.binding("mindmap.fbl", "freeplane").claims().marker();

        assertEquals(matches, MarkerEvaluator.matches(marker, utf8(body)));
    }

    static Stream<Arguments> rootKeyBodies() {
        return Stream.of(
                Arguments.of("bundle:\n  name: x\n", true),
                Arguments.of("{ \"bundle\": { \"name\": \"x\" } }", true),
                Arguments.of("other: 1\n", false),
                Arguments.of("bundle: [unclosed\n", false));
    }

    @ParameterizedTest
    @MethodSource("rootKeyBodies")
    void aRootKeyMarkerReadsYamlAndJsonWithoutABinding(String body, boolean matches) {
        Marker marker = new Marker("bundle", null, null, null, 0);

        assertEquals(matches, MarkerEvaluator.matches(marker, utf8(body)));
    }

    @Test
    void aFirstLineMarkerIsMatchedAfterAByteOrderMark() {
        Marker marker = new Marker(null, null, "causal-loop", null, 0);
        byte[] text = utf8("causal-loop 1\n");
        byte[] body = new byte[3 + text.length];
        body[0] = (byte) 0xEF;
        body[1] = (byte) 0xBB;
        body[2] = (byte) 0xBF;
        System.arraycopy(text, 0, body, 3, text.length);

        assertTrue(MarkerEvaluator.matches(marker, body));
        assertFalse(MarkerEvaluator.matches(marker, utf8("# causal-loop\n")));
    }

    @Test
    void aRegistrationOnlyBindingIsNeverACandidate() {
        // databricks.yml and Chart.yaml are claimed only through registrations.
        List<FblBinding> job = Router.candidates("nightly.yml", utf8("resources:\n  jobs: {}\n"), ALL);
        List<FblBinding> chart = Router.candidates("Chart.yaml", utf8("apiVersion: v2\n"), ALL);

        assertEquals(List.of(), job);
        assertEquals(List.of(), chart);
    }

    @Test
    void anExtensionIsMatchedIgnoringCase() {
        List<FblBinding> candidates = Router.candidates("Plan.TML", utf8("elements: []\n"), ALL);

        assertEquals(List.of("timeline"), candidates.stream().map(FblBinding::name).toList());
    }

    @Test
    void severalCandidatesAreAllReturned() {
        // Two bindings claiming the same extension.
        FblBinding a = claiming("a", ".x");
        FblBinding b = claiming("b", ".x");

        List<FblBinding> candidates = Router.candidates("file.x", new byte[0], List.of(a, b));

        // The router never chooses on the caller's behalf.
        assertEquals(List.of(a, b), candidates);
    }

    @Test
    void aReadingWhoseSuggestMatchesIsOfferedFirst() {
        FblBinding turtle = CorpusBindings.binding("w3c-turtle.fbl", "turtle");
        byte[] skos = utf8("@prefix skos: <http://www.w3.org/2004/02/skos/core#> .\nex:s a skos:ConceptScheme .\n");

        List<String> readings = Router.readings(turtle, skos);

        assertEquals(List.of("w3c/skos", "w3c/rdf", "w3c/owl", "w3c/shacl"), readings);
        assertEquals("w3c/rdf", Router.bare(turtle));
    }

    @ParameterizedTest
    @CsvSource({
            "templates/**, templates/a/b.yaml, true",
            "templates/**, values.yaml, false",
            "**/.helmignore, .helmignore, true",
            "**/.helmignore, charts/x/.helmignore, true",
            "charts/*/Chart.yaml, charts/x/Chart.yaml, true",
            "charts/*/Chart.yaml, charts/x/y/Chart.yaml, false",
            "values*.yaml, values.prod.yaml, true",
            "file?.[ab], file1.a, true",
            "file?.[!ab], file1.a, false",
    })
    void aGlobMatchesAsFblDefinesIt(String glob, String path, boolean matches) {
        assertEquals(matches, Glob.isMatch(glob, path, false));
    }

    @Test
    void aFolderIsRecognisedAndItsFilesSelectedWithoutFollowingLinks() throws IOException {
        FblBinding chart = CorpusBindings.binding("helm-chart.fbl", "chart");
        try (TemporaryFolder folder = new TemporaryFolder(); TemporaryFolder outside = new TemporaryFolder()) {
            folder.write("Chart.yaml", utf8("apiVersion: v2\n"));
            folder.write("values.yaml", utf8("a: 1\n"));
            folder.write("templates/deployment.yaml", utf8("kind: Deployment\n"));
            folder.write(".git/config", utf8("x\n"));
            folder.write("README.md", utf8("# chart\n"));
            outside.write("secret.yaml", utf8("x: 1\n"));
            folder.link("templates/linked", outside.root());

            boolean recognised = FolderSubject.recognise(chart, folder.root());
            List<String> files = FolderSubject.files(chart, folder.root()).stream().map(FolderFile::relativePath).toList();

            // Ordinal order, .git ignored, README unselected, the link not followed.
            assertTrue(recognised);
            assertEquals(List.of("Chart.yaml", "templates/deployment.yaml", "values.yaml"), files);
            Files.delete(folder.root().resolve("Chart.yaml"));
            assertFalse(FolderSubject.recognise(chart, folder.root()));
        }
    }
}
