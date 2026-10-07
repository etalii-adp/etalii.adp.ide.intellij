package etalii.adp.fbl.document;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import etalii.adp.fbl.support.Corpus;
import etalii.adp.fbl.support.TemporaryFolder;

/** Loading an FBL document (FBL §2.1, §2.3, §2.7, §14.1). The counterparts of standalone's {@code Loading/Loading.Tests.cs}. */
class LoadingTest {

    private static final String ITEM = """
            { "name": "item", "type": "Item", "at": "/items/*", "id": { "from": { "key": "id" } }, "attributes": { "label": { "key": "label" } } }""";

    private static final String CLAIMS = "{ \"extensions\": [\".t\"] }";
    private static final String DECLARED = "\"declared\"";
    private static final String ITEMS = "[" + ITEM + "]";

    private static String binding(String claims, String reader, String elements) {
        return "{ \"claims\": " + claims + ", \"body\": { \"kind\": \"file\", \"family\": \"yaml\" }, \"reader\": " + reader + ", \"elements\": " + elements + " }";
    }

    private static String binding() {
        return binding(CLAIMS, DECLARED, ITEMS);
    }

    private static byte[] document(String version, String bindings) {
        return ("{ \"fbl\": \"" + version + "\", \"bindings\": { " + bindings + " } }").getBytes(UTF_8);
    }

    private static List<Problem> load(String binding, String version) {
        return FblDocumentLoader.load(document(version, "\"t\": " + binding), null).problems();
    }

    private static List<Problem> load(String binding) {
        return load(binding, "0.1");
    }

    private static boolean hasErrorAt(List<Problem> problems, String pointer) {
        return problems.stream().anyMatch(p -> p.severity() == ProblemSeverity.ERROR && p.pointer().equals(pointer));
    }

    @Test
    void aValidDocumentLoadsWithoutAProblem() {
        FblDocumentLoader.Loaded loaded = FblDocumentLoader.load(document("0.1", "\"t\": " + binding()), null);

        // The baseline every refusal below departs from by one change.
        assertEquals(List.of(), loaded.problems());
        List<Rule> elements = loaded.document().bindings().get("t").elements();
        assertEquals(1, elements.size());
        assertEquals("item", elements.get(0).name());
    }

    @Test
    void aDuplicateKeyAnywhereIsRejectedAtItsPointer() {
        List<Problem> problems = load(binding(CLAIMS, DECLARED, """
                [{ "name": "item", "type": "Item", "type": "Other", "at": "/items/*" }]"""));

        assertEquals(1, problems.size());
        assertEquals(ProblemSeverity.ERROR, problems.get(0).severity());
        assertEquals("/bindings/t/elements/0/type", problems.get(0).pointer());
    }

    @ParameterizedTest
    @ValueSource(strings = {"1.0", "2.3"})
    void anotherMajorVersionIsRefused(String version) {
        List<Problem> problems = load(binding(), version);

        assertTrue(hasErrorAt(problems, "/fbl"), problems.toString());
    }

    @Test
    void aNewerMinorVersionLoadsWithAWarning() {
        FblDocumentLoader.Loaded loaded = FblDocumentLoader.load(document("0.2", "\"t\": " + binding()), null);

        assertNotNull(loaded.document());
        assertEquals(1, loaded.problems().size());
        assertEquals(ProblemSeverity.WARNING, loaded.problems().get(0).severity());
    }

    @ParameterizedTest
    @MethodSource("unresolvedNames")
    void aNameThatDoesNotResolveIsRejectedAtItsPointer(String elements, String pointer) {
        List<Problem> problems = load(binding(CLAIMS, DECLARED, elements));

        assertTrue(hasErrorAt(problems, pointer), problems.toString());
    }

    static Stream<Arguments> unresolvedNames() {
        return Stream.of(
                Arguments.of("""
                        [{ "name": "item", "type": "Item", "at": "/items/*", "parent": { "rules": ["missing"], "slot": "parent" } }]""",
                        "/bindings/t/elements/0/parent/rules"),
                Arguments.of("""
                        [{ "name": "item", "type": "Item", "at": "/items/*", "remove": { "cascade": ["missing"] } }]""",
                        "/bindings/t/elements/0/remove/cascade"),
                Arguments.of("""
                        [{ "name": "item", "type": "Item", "at": "/items/*", "within": ["missing"] }]""",
                        "/bindings/t/elements/0/within"),
                Arguments.of("""
                        [{ "name": "item", "type": "Item", "at": "/items/*", "files": ["missing"] }]""",
                        "/bindings/t/elements/0/files"),
                Arguments.of("""
                        [{ "name": "item", "type": "Item", "at": "/items/*", "attributes": { "owner": { "key": "owner", "reference": { "to": ["missing"] } } } }]""",
                        "/bindings/t/elements/0/attributes/owner/reference/to"),
                Arguments.of("""
                        [{ "name": "item", "type": "Item", "at": "/items/*" }, { "name": "item", "type": "Other", "at": "/others/*" }]""",
                        "/bindings/t/elements/1/name"));
    }

    @ParameterizedTest
    @MethodSource("stepSixChecks")
    void aStepSixCheckIsApplied(String claims, String reader, String elements, String pointer) {
        List<Problem> problems = load(binding(claims, reader, elements));

        assertTrue(hasErrorAt(problems, pointer), problems.toString());
    }

    static Stream<Arguments> stepSixChecks() {
        return Stream.of(
                Arguments.of("{ \"extensions\": [\".t\"], \"shared\": true }", DECLARED, ITEMS, "/bindings/t/claims"),
                Arguments.of("{ \"extensions\": [\".t\"], \"readings\": { \"a\": { \"bare\": true }, \"b\": { \"bare\": true } } }", DECLARED, ITEMS,
                        "/bindings/t/claims/readings"),
                Arguments.of(CLAIMS, DECLARED, "[]", "/bindings/t"),
                Arguments.of(CLAIMS, "{ \"plugin\": \"x.y\" }", ITEMS, "/bindings/t"),
                Arguments.of(CLAIMS, DECLARED, """
                        [{ "name": "item", "type": "Item", "at": "/items/*", "attributes": { "label": { "key": "label", "text": true } } }]""",
                        "/bindings/t/elements/0/attributes/label"));
    }

    @Test
    void aSharedClaimWithAMarkerIsAccepted() {
        List<Problem> problems = load(binding("{ \"extensions\": [\".t\"], \"shared\": true, \"marker\": { \"rootKey\": \"items\" } }", DECLARED, ITEMS));

        assertEquals(List.of(), problems);
    }

    @Test
    void everyProblemIsReportedRatherThanTheFirst() {
        List<Problem> problems = load(binding("{ \"extensions\": [\".t\"], \"shared\": true }", DECLARED, """
                [{ "name": "item", "type": "Item", "at": "/items/*", "line": "(a)\\\\1", "remove": { "cascade": ["missing"] } }]"""));

        // The claim, the rule's two anchors and the cascade, each with its own pointer.
        assertTrue(problems.stream().anyMatch(p -> p.pointer().equals("/bindings/t/claims")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.pointer().equals("/bindings/t/elements/0")), problems.toString());
        assertTrue(problems.stream().anyMatch(p -> p.pointer().equals("/bindings/t/elements/0/remove/cascade")), problems.toString());
        assertTrue(problems.stream().noneMatch(p -> p.message().isBlank()), problems.toString());
    }

    @Test
    void aReferenceResolvesAgainstTheReferringDocument() {
        try (TemporaryFolder folder = new TemporaryFolder()) {
            Path document = folder.write("bindings/plan.fbl",
                    document("0.1", "\"t\": " + binding() + ", \"u\": " + binding("{ \"extensions\": [\".u\"] }", DECLARED, ITEMS)));
            Path fixture = folder.write("fixtures/one/fixture.json", "{}".getBytes(UTF_8));

            FblBinding across = FblDocumentLoader.resolveReference("../../bindings/plan.fbl#u", fixture).binding();
            FblBinding within = FblDocumentLoader.resolveReference("#t", document).binding();

            assertEquals("u", across.name());
            assertEquals("t", within.name());
            assertThrows(IllegalStateException.class, () -> FblDocumentLoader.resolveReference("#missing", document));
        }
    }

    /** Not in the baseline: the bindings FBL publishes are the first thing this loader must take. */
    @Test
    void everyCopiedBindingLoads() {
        List<String> documents = Corpus.files(Corpus.conformance()).stream().filter(f -> f.endsWith(".fbl") && !f.contains("/")).toList();
        assertEquals(8, documents.size(), documents.toString());

        for (String document : documents) {
            FblDocumentLoader.Loaded loaded = FblDocumentLoader.load(Corpus.conformance().resolve(document));

            assertNotNull(loaded.document(), document + ": " + loaded.problems());
            assertFalse(loaded.document().bindings().isEmpty(), document);
            assertEquals(List.of(), loaded.problems().stream().filter(p -> p.severity() == ProblemSeverity.ERROR).toList(), document);
        }
    }
}
