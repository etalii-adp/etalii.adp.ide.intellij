package etalii.adp.fbl.real;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.fbl.FblElement;
import etalii.adp.fbl.FblModel;
import etalii.adp.fbl.FblView;
import etalii.adp.fbl.Finding;
import etalii.adp.fbl.FindingCodes;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.history.OpenBody;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.plan.PlanResult;
import etalii.adp.fbl.registration.BodyLocation;
import etalii.adp.fbl.registration.BodyLocator;
import etalii.adp.fbl.registration.OpenRegistration;
import etalii.adp.fbl.registration.RegistrationDocument;
import etalii.adp.fbl.registration.RegistrationEntry;
import etalii.adp.fbl.registration.RegistrationHeader;
import etalii.adp.fbl.support.CorpusBindings;

/**
 * Every {@code .adp} registration under {@code fbl/testdata/real/} (spec 010, FR-020): parsed in
 * the line form and written back unchanged; for a declared binding's origin, its body found and
 * opened, its {@code view} or {@code resource} selecting something, and its layout naming elements
 * of the reading or reported stale. The counterparts of standalone's
 * {@code RealFiles/Registrations.Tests.cs}; its four tests over files this corpus does not hold
 * have none (see {@code fbl-test-inventory.md}).
 */
class RegistrationsTest {

    static Stream<String> registrations() {
        return RealFileCorpus.registrations().stream();
    }

    /** The registrations whose origin a declared file binding claims: the others do not concern the test of the body. */
    static Stream<String> declaredRegistrations() {
        List<String> files = RealFileCorpus.registrations().stream()
                .filter(file -> declared(RegistrationDocument.read(RealFileCorpus.bytes(file)).origin()) != null).toList();
        assertFalse(files.isEmpty(), "No registration has an origin a declared binding claims.");
        return files.stream();
    }

    @Test
    void theEnumerationFindsTheRegistrations() {
        // Act.
        int count = RealFileCorpus.selectRegistrations().size();

        // Assert.
        assertTrue(count >= RealFileCorpus.MINIMUM_REGISTRATIONS, () -> count + " registrations were found; at least "
                + RealFileCorpus.MINIMUM_REGISTRATIONS + " were there when this suite was written.");
    }

    @ParameterizedTest
    @MethodSource("registrations")
    void theRegistrationParsesAndSavesUnchanged(String file) {
        // Arrange.
        byte[] bytes = RealFileCorpus.bytes(file);

        // Act.
        OpenRegistration registration = OpenRegistration.open(bytes);
        PlanResult result = registration.change(new ModelChange.Save());

        // Assert.
        String origin = registration.document().origin();
        assertFalse(origin == null || origin.isBlank(), () -> file + ": no origin on line 1.");
        assertEquals(List.of(), assertInstanceOf(PlanResult.Planned.class, result).edit().splices());
        assertArrayEquals(bytes, registration.bytes());
    }

    @ParameterizedTest
    @MethodSource("declaredRegistrations")
    void aDeclaredBindingsRegistrationOpensItsBody(String file) {
        // Arrange.
        RegistrationDocument registration = RegistrationDocument.read(RealFileCorpus.bytes(file));
        CorpusBindings.Named named = declared(registration.origin());
        assertNotNull(named);
        FblBinding binding = named.binding();
        String reference = named.document() + "#" + binding.name();
        Path root = RealFileCorpus.real();

        // Act.
        BodyLocation location = BodyLocator.locate(RealFileCorpus.fullPath(file), registration, binding, root);

        // Assert: the body resolves and exists.
        assertTrue(location.refusal() == null, () -> file + ": " + location.refusal());
        Divergences.check("registration-body", reference, file, location.exists() ? null : "no body at " + relative(root, location.path()));
        if (!location.exists()) {
            return;
        }
        String bodyFile = relative(root, location.path());
        Map<String, String> headers = new LinkedHashMap<>();
        for (RegistrationHeader header : registration.headers()) {
            assertTrue(headers.put(header.key(), header.value()) == null, () -> file + ": the header '" + header.key() + "' is given twice.");
        }
        OpenBody body = OpenBody.open(RealFileCorpus.bytes(bodyFile), binding, RealFileCorpus.options(binding, bodyFile, headers));
        FblModel model = body.model();
        if (model.unreadable()) {
            return;
        }

        // Assert: the view and the resource select something.
        String view = registration.view();
        if (view != null) {
            Divergences.check("registration-view", reference, file, model.selectView(view) == null
                    ? "no view '" + view + "' among " + model.views().stream().map(FblView::name).collect(Collectors.joining(", "))
                    : null);
        }
        String resource = registration.resource();
        if (resource != null) {
            Divergences.check("registration-resource", reference, file,
                    model.resources().contains(resource) ? null : "no resource '" + resource + "' among [" + String.join(", ", model.resources()) + "]");
        }

        // Assert: every layout entry names an element of the reading, or is reported stale.
        Set<String> ids = model.elements().stream().map(FblElement::id).collect(Collectors.toSet());
        List<Finding> stale = registration.staleEntries(ids, file);
        List<RegistrationEntry> entries = registration.layout() == null ? List.of() : registration.layout().entries();
        List<String> unknown = entries.stream().map(RegistrationEntry::key).filter(id -> !ids.contains(id)).sorted().toList();
        assertEquals(unknown.size(), stale.size());
        for (Finding finding : stale) {
            assertEquals(FindingCodes.STALE_VIEW_DATA, finding.code());
        }
        Divergences.check("registration-layout", reference, file, unknown.isEmpty() ? null
                : unknown.size() + " layout entries name no element: " + unknown.stream().limit(5).collect(Collectors.joining(", "))
                        + (unknown.size() > 5 ? ", …" : ""));
    }

    private static String relative(Path root, Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    /** The copied declared file binding whose {@code claims.origins} holds {@code origin}, with its document; null when there is none. */
    private static CorpusBindings.Named declared(String origin) {
        return CorpusBindings.all().stream()
                .filter(b -> b.binding().plugin() == null && !b.binding().body().isFolder() && b.binding().claims().origins().contains(origin))
                .findFirst().orElse(null);
    }
}
