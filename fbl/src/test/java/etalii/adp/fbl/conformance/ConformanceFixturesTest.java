package etalii.adp.fbl.conformance;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import etalii.adp.fbl.FblElement;
import etalii.adp.fbl.FblModel;
import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.Splice;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.FblDocumentLoader;
import etalii.adp.fbl.document.ProblemSeverity;
import etalii.adp.fbl.history.OpenBody;
import etalii.adp.fbl.history.UndoResult;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.plan.PlanResult;
import etalii.adp.fbl.registration.OpenRegistration;
import etalii.adp.fbl.rule.BodyReading;
import etalii.adp.fbl.support.Corpus;
import etalii.adp.fbl.support.NaturalIds;

/**
 * Every round-trip fixture copied from etalii-adp/etalii.adp, run through the library as FBL
 * §15.3 says a host passes one: reading the input gives what {@code read} lists, and every step
 * produces exactly its splices and its document, or its refusal with nothing written. The
 * counterparts of standalone's {@code Conformance/ConformanceFixtures.Tests.cs}.
 */
class ConformanceFixturesTest {

    /** The number of fixtures copied when this suite was written: fewer means the enumeration broke. */
    private static final int MINIMUM_FIXTURES = 8;

    /** The names of the fixture folders, in ordinal order. */
    static List<String> fixtures() {
        try (Stream<Path> folders = Files.list(Corpus.conformance().resolve("fixtures"))) {
            return folders.filter(Files::isDirectory)
                    .filter(folder -> Files.exists(folder.resolve("fixture.json")))
                    .map(folder -> folder.getFileName().toString())
                    .sorted(Comparator.naturalOrder())
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void theFixturesAreFound() {
        int count = fixtures().size();

        assertTrue(count >= MINIMUM_FIXTURES, () -> "Only " + count + " fixtures were found; " + MINIMUM_FIXTURES + " are vendored.");
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void theFixturePasses(String name) {
        // Arrange.
        Path folder = Corpus.conformance().resolve("fixtures").resolve(name);
        Path fixturePath = folder.resolve("fixture.json");
        JsonObject root = JsonParser.parseString(new String(Corpus.bytes(fixturePath), UTF_8)).getAsJsonObject();
        FblDocumentLoader.Resolved resolved = FblDocumentLoader.resolveReference(property(root, "binding").getAsString(), fixturePath);
        FblBinding binding = resolved.binding();
        assertFalse(resolved.problems().stream().anyMatch(p -> p.severity() == ProblemSeverity.ERROR), () -> resolved.problems().toString());
        String inputName = property(root, "input").getAsString();
        byte[] input = Corpus.bytes(folder.resolve(inputName));
        Subject subject = Subject.open(input, inputName, binding);

        // Act and assert: the reading.
        if (root.has("read")) {
            assertRead(subject, root.getAsJsonObject("read"), name);
        }

        // Act and assert: every step.
        int index = 0;
        for (JsonElement stepElement : property(root, "steps").getAsJsonArray()) {
            JsonObject step = stepElement.getAsJsonObject();
            index++;
            String label = name + " step " + index;
            byte[] before = subject.bytes();
            List<Splice> expected = new ArrayList<>();
            for (JsonElement splice : property(step, "splices").getAsJsonArray()) {
                expected.add(readSplice(splice.getAsJsonObject()));
            }
            List<Splice> actual;
            if (step.has("undo") && step.get("undo").getAsBoolean()) {
                actual = subject.undo(label);
            } else if (step.has("redo") && step.get("redo").getAsBoolean()) {
                actual = subject.redo(label);
            } else {
                PlanResult result = subject.change(readChange(property(step, "edit").getAsJsonObject()));
                if (step.has("refused")) {
                    PlanResult.Refused refusal = assertInstanceOf(PlanResult.Refused.class, result);
                    assertEquals(step.get("refused").getAsString(), refusal.reason());
                    assertArrayEquals(before, subject.bytes());
                    actual = List.of();
                } else if (result instanceof PlanResult.Planned planned) {
                    actual = planned.edit().splices();
                } else {
                    actual = fail(label + ": refused with '" + (result instanceof PlanResult.Refused refused ? refused.reason() : "") + "'.");
                }
            }
            List<Splice> produced = actual;
            // The splices first: operation, start, end and text, which is what two splices are equal by.
            assertTrue(expected.equals(produced), () -> label + ": expected splices\n  " + lines(expected) + "\nbut planned\n  " + lines(produced));
            byte[] expect = step.has("expect")
                    ? step.get("expect").getAsString().getBytes(UTF_8)
                    : Corpus.bytes(folder.resolve(property(step, "expectFile").getAsString()));
            byte[] document = subject.bytes();
            assertTrue(Arrays.equals(expect, document),
                    () -> label + ": the document differs from 'expect'.\nExpected:\n" + new String(expect, UTF_8) + "\nActual:\n" + new String(document, UTF_8));
        }
    }

    @ParameterizedTest
    @MethodSource("fixtures")
    void everyByteOfTheInputBelongsToTheReading(String name) {
        // Arrange.
        Path folder = Corpus.conformance().resolve("fixtures").resolve(name);
        Path fixturePath = folder.resolve("fixture.json");
        JsonObject root = JsonParser.parseString(new String(Corpus.bytes(fixturePath), UTF_8)).getAsJsonObject();
        String inputName = property(root, "input").getAsString();
        if (isRegistration(inputName)) {
            return;
        }
        FblBinding binding = FblDocumentLoader.resolveReference(property(root, "binding").getAsString(), fixturePath).binding();

        // Act.
        BodyReading reading = BodyReading.read(Corpus.bytes(folder.resolve(inputName)), binding, FblOptions.DEFAULT.withFileName(inputName));

        // Assert: the byte-coverage invariant of FBL §4.1.
        assertNull(reading.unreadable());
        assertEquals(List.of(), reading.family().unaccounted());
    }

    private static void assertRead(Subject subject, JsonObject read, String name) {
        FblModel model = subject.model();
        if (read.has("unreadable")) {
            assertEquals(read.get("unreadable").getAsBoolean(), model.unreadable());
        }
        if (read.has("elements")) {
            for (JsonElement item : read.getAsJsonArray("elements")) {
                JsonObject element = item.getAsJsonObject();
                String id = property(element, "id").getAsString();
                String type = property(element, "type").getAsString();
                FblElement found = model.find(id);
                assertTrue(found != null, () -> name + ": no element '" + id + "' was read; read: "
                        + model.elements().stream().map(e -> e.id() + " (" + e.type() + ")").collect(Collectors.joining(", ")) + ".");
                assertEquals(type, found.type());
            }
        }
        if (read.has("findings")) {
            for (JsonElement item : read.getAsJsonArray("findings")) {
                JsonObject finding = item.getAsJsonObject();
                String code = property(finding, "rule").getAsString();
                Integer line = finding.has("line") ? Integer.valueOf(finding.get("line").getAsInt()) : null;
                assertTrue(
                        model.findings().stream().anyMatch(f -> f.code().equals(code)
                                && (line == null || (f.location() != null && f.location().line() == line))),
                        () -> name + ": no finding " + code + (line == null ? "" : " on line " + line) + " among " + model.findings());
            }
        }
    }

    // A splice's "file", which FBL gives for folder subjects, is not read: no fixture has a folder subject, and the source's runner reads none either.
    private static Splice readSplice(JsonObject splice) {
        return new Splice(
                SpliceOperation.parse(property(splice, "operation").getAsString()),
                property(splice, "start").getAsInt(),
                property(splice, "end").getAsInt(),
                property(splice, "text").getAsString());
    }

    private static ModelChange readChange(JsonObject edit) {
        if (edit.has("save")) {
            return new ModelChange.Save();
        }
        if (edit.has("set")) {
            JsonObject set = edit.getAsJsonObject("set");
            return new ModelChange.Set(property(set, "element").getAsString(), attributes(property(set, "attributes").getAsJsonObject()));
        }
        if (edit.has("add")) {
            // An add's "after" is not read, as the source's runner does not read it: a model change has no place for it.
            JsonObject add = edit.getAsJsonObject("add");
            return new ModelChange.Add(
                    property(add, "type").getAsString(),
                    add.has("id") ? add.get("id").getAsString() : null,
                    add.has("attributes") ? attributes(add.getAsJsonObject("attributes")) : new LinkedHashMap<String, Object>(),
                    add.has("parent") ? add.get("parent").getAsString() : null);
        }
        if (edit.has("remove")) {
            return new ModelChange.Remove(property(edit.getAsJsonObject("remove"), "element").getAsString());
        }
        if (edit.has("place")) {
            JsonObject place = edit.getAsJsonObject("place");
            return new ModelChange.Place(property(place, "element").getAsString(), property(place, "x").getAsDouble(), property(place, "y").getAsDouble());
        }
        throw new IllegalStateException("Unknown fixture edit: " + edit + ".");
    }

    private static Map<String, Object> attributes(JsonObject attributes) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> member : attributes.entrySet()) {
            values.put(member.getKey(), value(member.getValue()));
        }
        return values;
    }

    /** A fixture's value as the model holds it: text, a whole number, a number, a boolean, a list of these, or null. */
    private static Object value(JsonElement value) {
        if (value.isJsonPrimitive()) {
            JsonPrimitive primitive = value.getAsJsonPrimitive();
            if (primitive.isString()) {
                return primitive.getAsString();
            }
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            // A number written without a fraction or an exponent is a whole number; any other is a double.
            try {
                return Long.parseLong(primitive.getAsString());
            } catch (NumberFormatException e) {
                return primitive.getAsDouble();
            }
        }
        if (value.isJsonArray()) {
            List<Object> items = new ArrayList<>();
            for (JsonElement item : value.getAsJsonArray()) {
                items.add(value(item));
            }
            return items;
        }
        return null;
    }

    /** A member that has to be there: a fixture without it fails here, by its name. */
    private static JsonElement property(JsonObject object, String name) {
        JsonElement member = object.get(name);
        if (member == null) {
            throw new IllegalStateException("The fixture has no '" + name + "' in " + object + ".");
        }
        return member;
    }

    private static String lines(List<Splice> splices) {
        return splices.stream().map(Splice::toString).collect(Collectors.joining("\n  "));
    }

    private static boolean isRegistration(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".adp");
    }

    /** A fixture's subject: a body read through its binding, or a registration (an {@code .adp} input). */
    private static final class Subject {

        /** Null for a registration. */
        private final OpenBody body;

        /** Null for a body. */
        private final OpenRegistration registration;

        private Subject(OpenBody body, OpenRegistration registration) {
            this.body = body;
            this.registration = registration;
        }

        static Subject open(byte[] input, String name, FblBinding binding) {
            return isRegistration(name)
                    ? new Subject(null, OpenRegistration.open(input))
                    : new Subject(OpenBody.open(input, binding, FblOptions.DEFAULT.withFileName(name).withDeriveId(NaturalIds.forBinding(binding.name()))), null);
        }

        byte[] bytes() {
            return body != null ? body.bytes() : registration.bytes();
        }

        /** The body's model; null for a registration, which has none. */
        FblModel model() {
            return body != null ? body.model() : null;
        }

        PlanResult change(ModelChange change) {
            return body != null ? body.change(change) : registration.change(change);
        }

        List<Splice> undo(String label) {
            return done(body != null ? body.undo(null) : registration.undo(null), label);
        }

        List<Splice> redo(String label) {
            return done(body != null ? body.redo(null) : registration.redo(null), label);
        }

        private static List<Splice> done(UndoResult result, String label) {
            if (result instanceof UndoResult.Done done) {
                return done.splices();
            }
            return fail(label + ": refused with '" + (result instanceof UndoResult.Refused refused ? refused.reason() : "") + "'.");
        }
    }
}
