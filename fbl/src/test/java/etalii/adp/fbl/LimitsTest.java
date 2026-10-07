package etalii.adp.fbl;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.FblDocumentLoader;
import etalii.adp.fbl.history.OpenBody;

/**
 * The limits of FBL §16: a body over the size limit, or with more entries than the entry limit,
 * is unreadable with one finding rather than read in part. Standalone's suite has no test of
 * them, so these have no counterpart there.
 */
class LimitsTest {

    /** Three items: with the root, the {@code items} member and each item's {@code id}, more than three entries. */
    private static final String BODY = "items:\n  - id: a\n  - id: b\n  - id: c\n";

    private static final FblOptions OPTIONS = FblOptions.DEFAULT.withFileName("plan.t");

    /** A small binding with no header, so that nothing but a limit makes the body unreadable. */
    private static FblBinding binding() {
        String json = """
                { "fbl": "0.1", "bindings": { "t": { "claims": { "extensions": [".t"] }, "body": { "kind": "file", "family": "yaml" }, "reader": "declared", "elements":
                [{ "name": "item", "type": "Item", "at": "/items/*", "id": { "from": { "key": "id" } } }]
                 } } }""";
        FblDocumentLoader.Loaded loaded = FblDocumentLoader.load(json.getBytes(UTF_8), null);
        assertEquals(List.of(), loaded.problems());
        return loaded.document().bindings().get("t");
    }

    private static FblModel read(FblOptions options) {
        return OpenBody.open(BODY.getBytes(UTF_8), binding(), options).model();
    }

    @Test
    void aBodyOverTheSizeLimitIsUnreadableWithOneFinding() {
        int size = BODY.getBytes(UTF_8).length;

        // A body of exactly the limit is read: only a larger one is not.
        FblModel atTheLimit = read(OPTIONS.withMaxBodyBytes(size));
        FblModel overTheLimit = read(OPTIONS.withMaxBodyBytes(size - 1));

        assertFalse(atTheLimit.unreadable());
        assertEquals(3, atTheLimit.elements().size());
        assertTrue(overTheLimit.unreadable());
        assertEquals(1, overTheLimit.findings().size());
        assertEquals(FindingCodes.UNPARSEABLE, overTheLimit.findings().get(0).code());
        assertEquals("The body is larger than the " + (size - 1) + " bytes this host reads, so it is not read at all.", overTheLimit.findings().get(0).message());
        assertEquals(List.of(), overTheLimit.elements());
    }

    @Test
    void aBodyWithMoreEntriesThanTheLimitIsUnreadableWithOneFinding() {
        // The same body is read under the default limit, so it is the limit that makes it unreadable.
        FblModel underTheLimit = read(OPTIONS);
        FblModel overTheLimit = read(OPTIONS.withMaxEntries(3));

        assertFalse(underTheLimit.unreadable());
        assertEquals(3, underTheLimit.elements().size());
        assertTrue(overTheLimit.unreadable());
        assertEquals(1, overTheLimit.findings().size());
        assertEquals(FindingCodes.UNPARSEABLE, overTheLimit.findings().get(0).code());
        assertEquals("The body has more than the 3 entries this host reads, so it is not read at all.", overTheLimit.findings().get(0).message());
        assertEquals(List.of(), overTheLimit.elements());
    }
}
