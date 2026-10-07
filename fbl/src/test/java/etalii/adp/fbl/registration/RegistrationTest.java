package etalii.adp.fbl.registration;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.history.UndoResult;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.support.Corpus;
import etalii.adp.fbl.support.CorpusBindings;
import etalii.adp.fbl.support.TemporaryFolder;

/**
 * Finding the body (FBL §8.2), identities (FBL §8.6) and legacy sidecars (FBL §8.7). The
 * counterparts of standalone's {@code Registration/Registration.Tests.cs}.
 */
class RegistrationTest {

    private static final FblBinding TIMELINE = CorpusBindings.binding("timeline.fbl", "timeline");

    private static byte[] utf8(String text) {
        return text.getBytes(UTF_8);
    }

    private static String text(byte[] bytes) {
        return new String(bytes, UTF_8);
    }

    @Test
    void theBodyIsTheSiblingWithTheRegistrationsBaseName() {
        try (TemporaryFolder folder = new TemporaryFolder()) {
            Path registration = folder.write("plan.adp", utf8("generic/timeline\n"));
            Path body = folder.write("plan.tml", utf8("elements: []\n"));

            BodyLocation location = BodyLocator.locate(registration, RegistrationDocument.read(Corpus.bytes(registration)), TIMELINE, folder.root());

            assertEquals(body, location.path());
            assertTrue(location.exists());
        }
    }

    @Test
    void aMissingBodyOpensAsMissing() {
        try (TemporaryFolder folder = new TemporaryFolder()) {
            Path registration = folder.write("plan.adp", utf8("generic/timeline\nbody: gone.tml\n"));

            BodyLocation location = BodyLocator.locate(registration, RegistrationDocument.read(Corpus.bytes(registration)), TIMELINE, folder.root());

            assertTrue(location.isMissing());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = { "body: ../outside.tml\n", "body: /etc/passwd\n" })
    void aBodyOutsideTheWorkspaceIsRefused(String header) {
        try (TemporaryFolder folder = new TemporaryFolder()) {
            Path registration = folder.write("workspace/plan.adp", utf8("generic/timeline\n" + header));

            BodyLocation location = BodyLocator.locate(registration, RegistrationDocument.read(Corpus.bytes(registration)), TIMELINE,
                    folder.root().resolve("workspace"));

            assertNotNull(location.refusal());
            assertNull(location.path());
        }
    }

    @Test
    void aBodyReachedThroughALinkIsRefused() {
        try (TemporaryFolder folder = new TemporaryFolder(); TemporaryFolder outside = new TemporaryFolder()) {
            outside.write("plan.tml", utf8("elements: []\n"));
            folder.link("linked", outside.root());
            Path registration = folder.write("plan.adp", utf8("generic/timeline\nbody: linked/plan.tml\n"));

            BodyLocation location = BodyLocator.locate(registration, RegistrationDocument.read(Corpus.bytes(registration)), TIMELINE, folder.root());

            assertNotNull(location.refusal());
        }
    }

    @Test
    void anIdentityIsStoredInOrderAfterTheLayout() {
        OpenRegistration registration = OpenRegistration.open(utf8("wardley/map\r\nlayout:\r\n  a: 1 2\r\n"));

        registration.change(new ModelChange.Identify("Tea", "c2"));
        registration.change(new ModelChange.Identify("Cup", "c1"));
        registration.change(new ModelChange.Identify("Tea", "c3"));

        assertEquals("wardley/map\r\nlayout:\r\n  a: 1 2\r\nidentities:\r\n  Cup: c1\r\n  Tea: c3\r\n", text(registration.bytes()));
        assertEquals("c3", registration.document().identityMap().get("Tea"));
    }

    @Test
    void aLegacyLayoutIsReadForItsViewIgnoringCase() {
        LegacySidecar sidecar = LegacySidecar.open(utf8("{\n  \"SystemContext\": {\n    \"a\": { \"x\": 40, \"y\": 60.5 }\n  }\n}\n"));

        var positions = sidecar.positions("systemcontext");

        assertEquals(new RegistrationEntry.Position(40d, 60.5d), positions.get("a"));
        assertTrue(sidecar.positions("Containers").isEmpty());
    }

    @Test
    void aLegacyLayoutIsWrittenBySplicesAndUndone() {
        String original = "{\n  \"SystemContext\": {\n    \"a\": {\n      \"x\": 40,\n      \"y\": 60\n    }\n  }\n}\n";
        LegacySidecar sidecar = LegacySidecar.open(utf8(original));

        sidecar.change(s -> s.planPlace("systemContext", "a", 41.25, 60));
        sidecar.change(s -> s.planPlace("SystemContext", "b", 10, 20.0004));

        // Numbers replaced in place, a new member written as the file writes them.
        assertEquals(
                "{\n  \"SystemContext\": {\n    \"a\": {\n      \"x\": 41.25,\n      \"y\": 60\n    },\n    \"b\": {\n      \"x\": 10,\n      \"y\": 20\n    }\n  }\n}\n",
                text(sidecar.bytes()));
        assertEquals(new RegistrationEntry.Position(10d, 20d), sidecar.positions("SystemContext").get("b"));
        assertInstanceOf(UndoResult.Done.class, sidecar.undo());
        assertInstanceOf(UndoResult.Done.class, sidecar.undo());
        assertEquals(original, text(sidecar.bytes()));
    }

    @Test
    void legacyIdentitiesAreReadAndWritten() {
        LegacySidecar sidecar = LegacySidecar.open(utf8("{\r\n  \"Tea\": \"c1\"\r\n}\r\n"));

        sidecar.change(s -> s.planIdentify("Cup", "c2"));

        assertEquals("{\r\n  \"Tea\": \"c1\",\r\n  \"Cup\": \"c2\"\r\n}\r\n", text(sidecar.bytes()));
        assertEquals("c2", sidecar.identities().get("Cup"));
    }

    @Test
    void theSidecarPathIsBesideTheBodyWithItsBaseName() {
        Path path = LegacySidecar.pathFor("{base}.layout.json", Path.of("x", "bottling-mes.dsl"));

        assertEquals(Path.of("x").toAbsolutePath().resolve("bottling-mes.layout.json"), path);
    }
}
