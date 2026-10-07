package etalii.adp.fbl.rule;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import etalii.adp.fbl.FblModel;
import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.Finding;
import etalii.adp.fbl.FindingCodes;
import etalii.adp.fbl.SourceLocation;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.FblDocumentLoader;
import etalii.adp.fbl.document.ProblemSeverity;
import etalii.adp.fbl.history.OpenBody;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.plan.PlanResult;
import etalii.adp.fbl.registration.OpenRegistration;
import etalii.adp.fbl.registration.RegistrationDocument;
import etalii.adp.fbl.support.Corpus;

/**
 * Rule precedence, ids, headers and findings (FBL §5, §7.4, §7.5, §8.1, §8.5, §8.6). The
 * counterparts of standalone's {@code Reading/Reading.Tests.cs}.
 */
class ReadingTest {

    private static final FblBinding TIMELINE = binding("timeline.fbl", "timeline");

    /** A binding of the conformance corpus by its document and name, checked for load errors. */
    private static FblBinding binding(String document, String name) {
        FblDocumentLoader.Loaded loaded = FblDocumentLoader.load(Corpus.conformance().resolve(document));
        assertFalse(loaded.problems().stream().anyMatch(p -> p.severity() == ProblemSeverity.ERROR), () -> loaded.problems().toString());
        return loaded.document().bindings().get(name);
    }

    private static FblBinding inline(String elements) {
        return inline(elements, "");
    }

    private static FblBinding inline(String elements, String extra) {
        String json = "{ \"fbl\": \"0.1\", \"bindings\": { \"t\": { \"claims\": { \"extensions\": [\".t\"] }, \"body\": { \"kind\": \"file\", \"family\": \"yaml\" }, \"reader\": \"declared\", \"elements\": "
                + elements + extra + " } } }";
        FblDocumentLoader.Loaded loaded = FblDocumentLoader.load(json.getBytes(UTF_8), null);
        assertEquals(List.of(), loaded.problems());
        return loaded.document().bindings().get("t");
    }

    private static FblModel read(String body, FblBinding binding) {
        return read(body, binding, FblOptions.DEFAULT.withFileName("plan.t"));
    }

    private static FblModel read(String body, FblBinding binding, FblOptions options) {
        return OpenBody.open(body.getBytes(UTF_8), binding, options).model();
    }

    /** The one finding of a code: the test fails when there is none or more than one. */
    private static Finding single(List<Finding> findings, String code) {
        List<Finding> found = findings.stream().filter(f -> f.code().equals(code)).toList();
        assertEquals(1, found.size(), () -> "Findings of " + code + " among " + findings);
        return found.get(0);
    }

    @Test
    void theFirstRuleInBindingOrderTakesAnEntryAndAnEntryBecomesOneElement() {
        // Both rules select every item; only the second's when excludes nothing.
        FblBinding binding = inline("""
                [
                  { "name": "special", "type": "Special", "at": "/items/*", "when": "has(entry.special)", "id": { "from": { "key": "id" } } },
                  { "name": "plain", "type": "Plain", "at": "/items/*", "id": { "from": { "key": "id" } } }
                ]
                """);

        FblModel model = read("items:\n  - id: a\n    special: true\n  - id: b\n", binding);

        assertEquals(List.of("a:Special", "b:Plain"), model.elements().stream().map(e -> e.id() + ":" + e.type()).toList());
    }

    @Test
    void anEntryWithoutAnIdIsAddressedByItsPlaceAndMarkedNotStored() {
        FblBinding binding = inline("""
                [{ "name": "item", "type": "Item", "at": "/items/*" }]""");

        FblModel model = read("items:\n  - label: a\n", binding);

        assertEquals(1, model.elements().size());
        assertFalse(model.elements().get(0).idIsStored());
    }

    @Test
    void aSidecarIdIsTakenFromTheRegistrationsIdentities() {
        FblBinding binding = inline("""
                [{ "name": "item", "type": "Item", "at": "/items/*", "id": { "sidecar": { "key": "entry.label" } }, "attributes": { "label": { "key": "label" } } }]""");
        FblOptions options = FblOptions.DEFAULT.withFileName("plan.t").withIdentities(Map.of("Tea", "c1"));

        FblModel model = read("items:\n  - label: Tea\n  - label: Cup\n", binding, options);

        // The stored identity is used; an element without one is not given a stored id.
        assertEquals("c1", model.elements().get(0).id());
        assertTrue(model.elements().get(0).idIsStored());
        assertFalse(model.elements().get(1).idIsStored());
    }

    @Test
    void theSecondOfTwoEqualIdsIsReportedAndNotStored() {
        FblModel model = read("elements:\n  - id: a\n    label: One\n    begin: 2026-01-01\n  - id: a\n    label: Two\n    begin: 2026-02-01\n", TIMELINE);

        Finding duplicate = single(model.findings(), FindingCodes.DUPLICATE_ID);
        assertEquals(5, duplicate.location().line());
        assertTrue(model.elements().get(0).idIsStored());
        assertFalse(model.elements().get(1).idIsStored());
        assertNotEquals("a", model.elements().get(1).id());
    }

    @Test
    void aMissingHeaderMarkIsReportedAndTheBodyIsStillRead() {
        FblModel model = read("elements:\n  - id: a\n    label: One\n    begin: 2026-01-01\n", TIMELINE);

        assertTrue(model.findings().stream().anyMatch(f -> f.code().equals(FindingCodes.HEADER_MISMATCH)));
        assertEquals(1, model.elements().size());
    }

    @Test
    void aRequiredHeaderThatIsMissingMakesTheBodyUnreadableWithOneFinding() {
        FblBinding mindmap = binding("mindmap.fbl", "freeplane");

        OpenBody body = OpenBody.open("<notamap/>".getBytes(UTF_8), mindmap, FblOptions.DEFAULT.withFileName("plan.mm"));

        assertTrue(body.model().unreadable());
        assertTrue(body.isReadOnly());
        assertEquals(List.of(), body.model().elements());
        assertEquals(1, body.model().findings().size());
        assertEquals(FindingCodes.UNPARSEABLE, body.model().findings().get(0).code());
    }

    @Test
    void anEntryARuleMatchesButCannotReadIsReportedAndKept() {
        // The when expression fails on an entry whose count is not a number.
        FblBinding binding = inline("""
                [{ "name": "item", "type": "Item", "at": "/items/*", "when": "int(entry.count) > 0", "id": { "from": { "key": "id" } } }]""");
        String text = "items:\n  - id: a\n    count: 3\n  - id: b\n    count: many\n";

        OpenBody body = OpenBody.open(text.getBytes(UTF_8), binding, FblOptions.DEFAULT.withFileName("plan.t"));

        Finding unreadable = single(body.model().findings(), FindingCodes.UNREADABLE_ENTRY);
        assertEquals("plan.t", unreadable.location().file());
        assertEquals(4, unreadable.location().line());
        assertEquals(3, unreadable.location().column());
        assertTrue(unreadable.location().length() > 0);
        assertEquals(1, body.model().elements().size());
        assertEquals("a", body.model().elements().get(0).id());
        assertFalse(body.model().unreadable());
        assertEquals(text, new String(body.bytes(), UTF_8));
    }

    @Test
    void aStatementNoRuleReadsIsReportedWhenTheBindingAsksForIt() {
        FblBinding causalLoop = binding("causal-loop-diagram.fbl", "cld");

        FblModel model = read("causal-loop\n\nthis is not a statement\n", causalLoop, FblOptions.DEFAULT.withFileName("loop.cld"));

        Finding unbound = single(model.findings(), FindingCodes.UNBOUND_STATEMENT);
        assertEquals(new SourceLocation("loop.cld", 3, 1, 23), unbound.location());
    }

    @Test
    void planningIsDeterministic() {
        String text = "timeline: 1\nelements:\n  - id: a\n    label: One\n    begin: 2026-01-01\n";
        ModelChange change = new ModelChange.Set("a", Map.<String, Object>of("label", "Two: and more"));

        PlanResult first = OpenBody.open(text.getBytes(UTF_8), TIMELINE, FblOptions.DEFAULT).plan(change);
        PlanResult second = OpenBody.open(text.getBytes(UTF_8), TIMELINE, FblOptions.DEFAULT).plan(change);

        assertEquals(
                assertInstanceOf(PlanResult.Planned.class, first).edit().splices(),
                assertInstanceOf(PlanResult.Planned.class, second).edit().splices());
    }

    @Test
    void anUnknownRegistrationHeaderIsKeptAndReported() {
        byte[] bytes = "generic/timeline\r\nbody: plan.tml\r\ncolour: green\r\n".getBytes(UTF_8);

        RegistrationDocument registration = RegistrationDocument.read(bytes);
        List<Finding> findings = registration.unknownHeaders(List.of(), "plan.adp");

        assertEquals(1, findings.size());
        Finding unknown = findings.get(0);
        assertEquals(FindingCodes.UNKNOWN_HEADER, unknown.code());
        assertEquals(3, unknown.location().line());
        assertEquals(List.of(), registration.unknownHeaders(List.of("colour"), "plan.adp"));
        assertArrayEquals(bytes, OpenRegistration.open(bytes).bytes());
    }

    @Test
    void aStaleLayoutEntryIsReportedAndRemovedAtTheNextWrite() {
        OpenRegistration registration = OpenRegistration.open("generic/timeline\r\nlayout:\r\n  a: 1 2\r\n  gone: 3 4\r\n".getBytes(UTF_8));
        registration.setKnownIds(Set.of("a"));

        List<Finding> stale = registration.document().staleEntries(registration.knownIds(), "plan.adp");
        registration.change(new ModelChange.Place("a", 5, 6));

        assertEquals(1, stale.size());
        assertEquals(FindingCodes.STALE_VIEW_DATA, stale.get(0).code());
        assertEquals("generic/timeline\r\nlayout:\r\n  a: 5 6\r\n", new String(registration.bytes(), UTF_8));
    }
}
