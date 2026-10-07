package etalii.adp.fbl.history;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import etalii.adp.fbl.Edit;
import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.Splice;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.FblDocumentLoader;
import etalii.adp.fbl.document.ProblemSeverity;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.registration.OpenRegistration;
import etalii.adp.fbl.support.Corpus;

/**
 * Undo, redo, snapshots, drift (FBL §7.1, §7.2) and the atomic save (FBL §6.6). The counterparts
 * of standalone's {@code History/History.Tests.cs}.
 */
class HistoryTest {

    private static final String TIMELINE = "elements:\n  - id: a\n    label: Alpha\n    start: 2026-01-01\n";

    /** A binding of the conformance corpus by its document and name, checked for load errors. */
    private static FblBinding binding(String document, String name) {
        FblDocumentLoader.Loaded loaded = FblDocumentLoader.load(Corpus.conformance().resolve(document));
        assertFalse(loaded.problems().stream().anyMatch(p -> p.severity() == ProblemSeverity.ERROR), () -> loaded.problems().toString());
        return loaded.document().bindings().get(name);
    }

    private static OpenBody open(String text) {
        return OpenBody.open(text.getBytes(UTF_8), binding("timeline.fbl", "timeline"), FblOptions.DEFAULT.withFileName("plan.tml"));
    }

    private static ModelChange labelBeta() {
        return new ModelChange.Set("a", Map.<String, Object>of("label", "Beta"));
    }

    @Test
    void aSnapshotUndoEqualsAnInverseSpliceUndo() {
        byte[] bytes = TIMELINE.getBytes(UTF_8);
        Edit edit = new Edit(List.of(new Splice(SpliceOperation.REPLACE_VALUE, 31, 36, "\"Al: pha\"")));
        OpenRegistration inverse = OpenRegistration.open(bytes);
        OpenRegistration snapshot = OpenRegistration.open(bytes);

        inverse.apply(edit);
        snapshot.apply(new Edit(edit.splices(), true));
        inverse.undo(null);
        snapshot.undo(null);

        assertArrayEquals(bytes, inverse.bytes());
        assertArrayEquals(inverse.bytes(), snapshot.bytes());
    }

    @Test
    void redoRepeatsTheEditAndIsRefusedOnDrift() {
        OpenBody body = open(TIMELINE);
        body.change(labelBeta());
        byte[] edited = body.bytes();
        body.undo(null);

        UndoResult drifted = body.redo("other".getBytes(UTF_8));
        UndoResult redone = body.redo(null);

        assertEquals(SplicedFile.DRIFT_REDO, assertInstanceOf(UndoResult.Refused.class, drifted).reason());
        assertInstanceOf(UndoResult.Done.class, redone);
        assertArrayEquals(edited, body.bytes());
    }

    @Test
    void aReloadClearsTheHistory() {
        OpenBody body = open(TIMELINE);
        body.change(labelBeta());

        body.reload(TIMELINE.getBytes(UTF_8));

        assertFalse(body.canUndo());
        assertEquals("Alpha", body.model().find("a").attributes().get("label"));
    }

    @Test
    void aSaveHandsTheHostsWriterTheEditedBytes() {
        OpenBody body = open(TIMELINE);
        body.change(labelBeta());
        List<byte[]> written = new ArrayList<>();

        body.save(written::add);

        // The atomic write itself is the host's, not the library's.
        assertEquals(1, written.size());
        assertArrayEquals(body.bytes(), written.get(0));
    }

    @Test
    void anUnreadableBodyIsNeverSaved() {
        OpenBody body = open("elements: [unclosed\n");
        List<byte[]> written = new ArrayList<>();

        Throwable refused = assertThrows(Throwable.class, () -> body.save(written::add));

        assertEquals(IllegalStateException.class, refused.getClass());
        assertTrue(body.isReadOnly());
        assertEquals(0, written.size());
    }
}
