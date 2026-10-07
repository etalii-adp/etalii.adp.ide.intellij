package etalii.adp.fbl.real;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.fbl.Splice;
import etalii.adp.fbl.document.AttributeBinding;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.Slot;
import etalii.adp.fbl.history.OpenBody;
import etalii.adp.fbl.history.SplicedFile;
import etalii.adp.fbl.history.UndoResult;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.plan.PlanResult;
import etalii.adp.fbl.real.RealFileCorpus.CorpusBinding;
import etalii.adp.fbl.rule.BodyReading;
import etalii.adp.fbl.rule.ReadElement;
import etalii.adp.fbl.rule.SlotRead;
import etalii.adp.fbl.rule.Unreadable;
import etalii.adp.fbl.text.Span;

/**
 * The copied declared bindings on every real file they take (spec 010, FR-019): read without
 * throwing and every byte accounted for, saved unchanged, edited and removed touching only the
 * splices' bytes and undone exactly, and an undo refused when the file has drifted. The
 * counterparts of standalone's {@code RealFiles/DeclaredBodies.Tests.cs}.
 */
class DeclaredBodiesTest {

    static Stream<String> keys() {
        return RealFileCorpus.keys();
    }

    static Stream<Arguments> pairs() {
        return RealFileCorpus.pairs();
    }

    @ParameterizedTest
    @MethodSource("keys")
    void theEnumerationFindsTheFiles(String key) {
        // Arrange.
        CorpusBinding binding = RealFileCorpus.find(key);

        // Act.
        List<String> files = RealFileCorpus.select(binding);

        // Assert.
        assertTrue(files.size() >= binding.minimum(),
                () -> key + ": " + files.size() + " files were found; at least " + binding.minimum() + " were there when this suite was written.");
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void theFileReads(String key, String file) {
        // Arrange.
        Loaded loaded = load(key, file);

        // Act.
        BodyReading reading = BodyReading.read(loaded.bytes(), loaded.binding(), RealFileCorpus.options(loaded.binding(), file));

        // Assert: unreadable only where listed, with the reason the reading gives.
        Unreadable problem = reading.unreadable();
        Divergences.check("unreadable", loaded.reference(), file, problem != null ? "offset " + problem.offset() + ": " + problem.message() : null);
        if (problem != null) {
            return;
        }
        List<Span> gaps = reading.family().unaccounted();
        assertTrue(gaps.isEmpty(), () -> file + ": bytes no node of the reading owns: "
                + gaps.stream().map(g -> g + " '" + reading.text().text(g) + "'").collect(Collectors.joining(", ")));
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void aSaveWithoutAnEditWritesTheBytesThatWereRead(String key, String file) {
        // Arrange.
        Loaded loaded = load(key, file);
        OpenBody body = OpenBody.open(loaded.bytes(), loaded.binding(), RealFileCorpus.options(loaded.binding(), file));

        // Act.
        PlanResult result = body.change(new ModelChange.Save());

        // Assert.
        if (body.isReadOnly()) {
            assertInstanceOf(PlanResult.Refused.class, result);
        } else {
            assertEquals(List.of(), assertInstanceOf(PlanResult.Planned.class, result).edit().splices());
        }
        assertArrayEquals(loaded.bytes(), body.bytes());
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void anEditChangesOnlyItsSplicesAndItsUndoRestoresTheFile(String key, String file) {
        // Arrange.
        Loaded loaded = load(key, file);
        byte[] bytes = loaded.bytes();
        OpenBody body = OpenBody.open(bytes, loaded.binding(), RealFileCorpus.options(loaded.binding(), file));
        Writable target = body.isReadOnly() ? null : firstWritable(body.reading());
        if (target == null) {
            return;
        }

        // Act.
        PlanResult result = body.change(new ModelChange.Set(target.element().id(), attributes(target.attribute(), target.value() + " edited")));

        // Assert.
        Divergences.check("edit", loaded.reference(), file,
                result instanceof PlanResult.Refused refused ? target.element().id() + "." + target.attribute() + ": " + refused.reason() : null);
        if (!(result instanceof PlanResult.Planned planned)) {
            return;
        }
        assertOnlySplicesChanged(bytes, body.bytes(), planned.edit().splices(), file);
        assertInstanceOf(UndoResult.Done.class, body.undo());
        assertArrayEquals(bytes, body.bytes());
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void aRemovalChangesOnlyItsSplicesAndItsUndoRestoresTheFile(String key, String file) {
        // Arrange.
        Loaded loaded = load(key, file);
        byte[] bytes = loaded.bytes();
        OpenBody body = OpenBody.open(bytes, loaded.binding(), RealFileCorpus.options(loaded.binding(), file));
        if (body.isReadOnly()) {
            return;
        }
        ReadElement element = body.reading().elements().stream().filter(e -> e.rule().remove() != null && e.rule().readOnly() == null).findFirst()
                .orElse(null);
        if (element == null) {
            return;
        }

        // Act.
        PlanResult result = body.change(new ModelChange.Remove(element.id()));

        // Assert.
        Divergences.check("remove", loaded.reference(), file, result instanceof PlanResult.Refused refused ? element.id() + ": " + refused.reason() : null);
        if (!(result instanceof PlanResult.Planned planned)) {
            return;
        }
        assertFalse(planned.edit().splices().isEmpty());
        assertOnlySplicesChanged(bytes, body.bytes(), planned.edit().splices(), file);
        assertFalse(body.model().elements().stream().anyMatch(e -> e.id().equals(element.id()) && e.type().equals(element.rule().type())));
        assertInstanceOf(UndoResult.Done.class, body.undo());
        assertArrayEquals(bytes, body.bytes());
    }

    @ParameterizedTest
    @MethodSource("pairs")
    void anUndoAfterTheFileChangedIsRefused(String key, String file) {
        // Arrange.
        Loaded loaded = load(key, file);
        OpenBody body = OpenBody.open(loaded.bytes(), loaded.binding(), RealFileCorpus.options(loaded.binding(), file));
        Writable target = body.isReadOnly() ? null : firstWritable(body.reading());
        if (target == null) {
            return;
        }
        if (!(body.change(new ModelChange.Set(target.element().id(), attributes(target.attribute(), target.value() + " edited"))) instanceof PlanResult.Planned)) {
            return;
        }
        byte[] edited = body.bytes();
        byte[] drifted = Arrays.copyOf(edited, edited.length + 1);
        drifted[edited.length] = '\n';

        // Act.
        UndoResult result = body.undo(drifted);

        // Assert.
        assertEquals(SplicedFile.DRIFT_UNDO, assertInstanceOf(UndoResult.Refused.class, result).reason());
        assertArrayEquals(edited, body.bytes());
    }

    @Test
    void everyListedDivergenceNamesAFileOfTheSuite() {
        // Arrange.
        Set<String> registrations = new HashSet<>(RealFileCorpus.registrations());

        // Act and assert.
        for (Divergences.Divergence divergence : Divergences.all()) {
            assertFalse(divergence.reason().isBlank(), () -> "The divergence on " + divergence.file() + " gives no reason.");
            assertTrue(Divergences.PROPERTIES.contains(divergence.property()),
                    () -> "divergences.json lists the property '" + divergence.property() + "', which no test checks.");
            boolean known = RealFileCorpus.DECLARED.stream()
                    .anyMatch(b -> b.reference().equals(divergence.binding()) && RealFileCorpus.files(b).contains(divergence.file()))
                    || registrations.contains(divergence.file());
            assertTrue(known, () -> "divergences.json lists " + divergence.file() + " for " + divergence.binding() + ", which the suite does not read.");
        }
    }

    /** A real file with the binding that takes it. */
    record Loaded(FblBinding binding, String reference, byte[] bytes) {
    }

    private static Loaded load(String key, String file) {
        CorpusBinding binding = RealFileCorpus.find(key);
        return new Loaded(RealFileCorpus.binding(binding), binding.reference(), RealFileCorpus.bytes(file));
    }

    private static Map<String, Object> attributes(String name, Object value) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(name, value);
        return attributes;
    }

    /** An attribute of an element that an edit of text can be tried on. */
    record Writable(ReadElement element, String attribute, String value) {
    }

    /**
     * The first element's first writable attribute: the first element in document order with an
     * attribute that is present, writable, a string, and neither its key, a reference nor a mapped
     * value, whose change would mean something else than an edit of text. Null when the reading has none.
     */
    static Writable firstWritable(BodyReading reading) {
        for (ReadElement element : reading.elements()) {
            if (element.isRelation() || element.rule().readOnly() != null) {
                continue;
            }
            for (Map.Entry<String, AttributeBinding> attribute : element.rule().attributes().entrySet()) {
                String name = attribute.getKey();
                AttributeBinding binding = attribute.getValue();
                if (binding.isComputed() || binding.parent() != null || binding.reference() != null || binding.map() != null || binding.flag()) {
                    continue;
                }
                if (name.equals(element.keyAttribute())) {
                    continue;
                }
                Slot from = element.rule().id() == null ? null : element.rule().id().from();
                if (from != null && Objects.equals(from.key(), binding.key()) && Objects.equals(from.xmlAttribute(), binding.xmlAttribute())
                        && Objects.equals(from.group(), binding.group()) && Objects.equals(from.capture(), binding.capture())) {
                    continue;
                }
                SlotRead read = element.slots().get(name);
                if (read == null || !read.present() || !read.writable() || !(read.value() instanceof String value) || value.isEmpty()) {
                    continue;
                }
                return new Writable(element, name, value);
            }
        }
        return null;
    }

    /** Every byte outside the splices is unchanged: the bytes between splices match, in order, before and after. */
    static void assertOnlySplicesChanged(byte[] before, byte[] after, List<Splice> splices, String file) {
        int position = 0;
        int shift = 0;
        for (Splice splice : splices) {
            int from = position;
            int length = splice.start() - position;
            assertTrue(from + shift >= 0 && from + shift + length <= after.length
                    && Arrays.equals(before, from, from + length, after, from + shift, from + shift + length),
                    () -> file + ": bytes " + from + ".." + splice.start() + " changed outside the edit's splices.");
            int text = splice.text().getBytes(UTF_8).length;
            shift += text - (splice.end() - splice.start());
            position = splice.end();
        }
        int tail = position;
        int moved = shift;
        assertTrue(after.length == before.length + moved && Arrays.equals(before, tail, before.length, after, tail + moved, after.length),
                () -> file + ": bytes after " + tail + " changed outside the edit's splices.");
    }
}
