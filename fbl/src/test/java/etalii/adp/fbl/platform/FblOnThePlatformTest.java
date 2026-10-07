package etalii.adp.fbl.platform;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertArrayEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.fixtures.BasePlatformTestCase;

import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.FblDocumentLoader;
import etalii.adp.fbl.history.OpenBody;
import etalii.adp.fbl.history.UndoResult;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.plan.PlanResult;
import etalii.adp.fbl.support.Corpus;
import etalii.adp.fbl.support.NaturalIds;

/**
 * The FBL library runs on the platform (spec 010, FR-023, SC-006): in a headless IDE, with no
 * network, it loads a binding FBL publishes, reads a fixture's body from a file of the test
 * project, saves it unchanged, makes the fixture's first edit and undoes it, and the file holds
 * the fixture's bytes each time. No tool uses the library yet, so this is where it is seen to run.
 */
@RunWith(JUnit4.class)
public class FblOnThePlatformTest extends BasePlatformTestCase {

    private static final String FIXTURE = "fixtures/timeline-edits/";

    @Test
    public void aFixtureIsWalkedOnThePlatform() throws IOException {
        byte[] input = Corpus.bytes(Corpus.conformance().resolve(FIXTURE + "roadmap.tml"));
        JsonObject fixture = JsonParser.parseString(new String(Corpus.bytes(Corpus.conformance().resolve(FIXTURE + "fixture.json")), UTF_8)).getAsJsonObject();
        byte[] afterTheEdit = fixture.getAsJsonArray("steps").get(1).getAsJsonObject().get("expect").getAsString().getBytes(UTF_8);
        FblBinding binding = FblDocumentLoader.load(Corpus.conformance().resolve("timeline.fbl")).document().bindings().get("timeline");

        VirtualFile file = WriteAction.computeAndWait(() -> myFixture.getTempDirFixture().createFile("roadmap.tml"));
        write(file, input);
        OpenBody body = OpenBody.open(file.contentsToByteArray(), binding, FblOptions.DEFAULT.withDeriveId(NaturalIds.forBinding("timeline")));

        // A save without an edit writes the bytes that were read: CRLF endings, the comment, the key no rule reads.
        body.save(bytes -> write(file, bytes));
        assertArrayEquals(input, file.contentsToByteArray());

        // The fixture's first edit: a new moment after the last element.
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("label", "Public beta");
        attributes.put("begin", "2025-10-01");
        attributes.put("row", 9L);
        PlanResult planned = body.change(new ModelChange.Add("Moment", "beta", attributes));
        assertTrue(planned.toString(), planned instanceof PlanResult.Planned);
        body.save(bytes -> write(file, bytes));
        assertArrayEquals(afterTheEdit, file.contentsToByteArray());

        // Its undo, against the bytes the file holds now, restores the input exactly.
        UndoResult undone = body.undo(file.contentsToByteArray());
        assertTrue(undone.toString(), undone instanceof UndoResult.Done);
        body.save(bytes -> write(file, bytes));
        assertArrayEquals(input, file.contentsToByteArray());
    }

    private static void write(VirtualFile file, byte[] bytes) {
        try {
            WriteAction.runAndWait(() -> file.setBinaryContent(bytes));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
