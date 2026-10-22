package etalii.adp.fbl.plan;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.FblDocumentLoader;
import etalii.adp.fbl.document.ProblemSeverity;
import etalii.adp.fbl.history.OpenBody;

/**
 * An insert's {@code constant} (FBL 0.8 §6.2, etalii.adp spec 033): the keys and values an insert writes into every
 * new entry as they are, whatever the add gives. A constant that {@code insert.keys} names is written at its place
 * there; every other one after the entry's other keys, in the constant's member order. Each value is written as its
 * JSON type, as §6.3 writes a new value; no other edit writes it. The counterparts of standalone's
 * {@code Planning/InsertConstant.Tests.cs}.
 */
class InsertConstantTest {

    private static final String CONSTANT = "{ \"kind\": \"task\", \"size\": 3, \"done\": false, \"format\": \"1.0\", \"none\": null }";

    private static FblBinding binding(String family, String keys) {
        String json = "{ \"fbl\": \"0.8\", \"bindings\": { \"t\": { \"claims\": { \"extensions\": [\".t\"] }, \"body\": { \"kind\": \"file\", \"family\": \"" + family
                + "\" }, \"reader\": \"declared\", \"text\": { \"quote\": \"double\" }, \"elements\": [{"
                + " \"name\": \"item\", \"type\": \"Item\", \"at\": \"/items/*\", \"id\": { \"from\": { \"key\": \"id\" } },"
                + " \"attributes\": { \"name\": { \"key\": \"name\" } },"
                + " \"insert\": { \"place\": \"end\", \"container\": \"/items\", \"keys\": " + keys + ", \"constant\": " + CONSTANT + " },"
                + " \"remove\": {} }] } } }";
        FblDocumentLoader.Loaded loaded = FblDocumentLoader.load(json.getBytes(UTF_8), null);
        assertEquals(List.of(), loaded.problems().stream().filter(p -> p.severity() == ProblemSeverity.ERROR).toList());
        return loaded.document().bindings().get("t");
    }

    private static OpenBody open(String text, FblBinding binding) {
        return OpenBody.open(text.getBytes(UTF_8), binding, FblOptions.DEFAULT);
    }

    private static String text(OpenBody body) {
        return new String(body.bytes(), UTF_8);
    }

    private static ModelChange addB() {
        return new ModelChange.Add("Item", "b", Map.of("name", "B"), null);
    }

    @Test
    void aYamlConstantNamedByKeysIsWrittenAtItsPlaceAndTheOthersAfterInMemberOrder() {
        OpenBody body = open("items:\n  - id: a\n    kind: task\n    name: A\n", binding("yaml", "[\"id\", \"kind\", \"name\"]"));

        PlanResult result = body.change(addB());

        // kind second; size, done, format and none after the name, as their JSON types, the string 1.0 quoted because it is not plain-safe (FBL 6.3).
        assertInstanceOf(PlanResult.Planned.class, result);
        assertEquals("items:\n  - id: a\n    kind: task\n    name: A\n  - id: b\n    kind: task\n    name: B\n    size: 3\n    done: false\n    format: \"1.0\"\n    none: null\n", text(body));
    }

    @Test
    void aYamlConstantNotNamedByKeysFollowsTheKeysTheRuleWrites() {
        OpenBody body = open("items:\n  - id: a\n    name: A\n", binding("yaml", "[]"));

        PlanResult result = body.change(addB());

        assertInstanceOf(PlanResult.Planned.class, result);
        assertEquals("items:\n  - id: a\n    name: A\n  - id: b\n    name: B\n    kind: task\n    size: 3\n    done: false\n    format: \"1.0\"\n    none: null\n", text(body));
    }

    @Test
    void aJsonConstantIsWrittenAsItsJsonType() {
        OpenBody body = open("{ \"items\": [ { \"id\": \"a\", \"name\": \"A\" } ] }\n", binding("json", "[\"id\", \"kind\", \"name\"]"));

        PlanResult result = body.change(addB());

        // A number, a bool and null as such, never as strings; the string 1.0 stays a string; kind second.
        assertInstanceOf(PlanResult.Planned.class, result);
        assertEquals("{ \"items\": [ { \"id\": \"a\", \"name\": \"A\" }, { \"id\": \"b\", \"kind\": \"task\", \"name\": \"B\", \"size\": 3, \"done\": false, \"format\": \"1.0\", \"none\": null } ] }\n",
                text(body));
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            yaml | 'items:\\n  - id: a\\n    kind: task\\n    name: A\\n'                    | 'items:\\n  - id: a\\n    kind: task\\n    name: Renamed\\n'
            json | '{ "items": [ { "id": "a", "kind": "task", "name": "A" } ] }\\n'       | '{ "items": [ { "id": "a", "kind": "task", "name": "Renamed" } ] }\\n'
            """)
    void aSetLeavesAConstantAsItIs(String family, String text, String expected) {
        // An entry that holds one constant and lacks the others.
        OpenBody body = open(text.replace("\\n", "\n"), binding(family, "[\"id\", \"kind\", \"name\"]"));

        PlanResult result = body.change(new ModelChange.Set("a", Map.of("name", "Renamed")));

        // Only the name changes; no constant is written, added or corrected by a set (FBL 6.4).
        assertInstanceOf(PlanResult.Planned.class, result);
        assertEquals(expected.replace("\\n", "\n"), text(body));
    }
}
