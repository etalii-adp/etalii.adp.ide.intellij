package etalii.adp.freemind.edit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import etalii.adp.freemind.FreeMindAsserts;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.parse.MindMapParser;

/**
 * Every reference scenario recorded from spec 001's implementation, replayed through the ported
 * format layer, gives the same bytes (FR-008, SC-003). The text is handled as the platform's
 * document holds it: separators converted to {@code \n} on load and restored on save.
 */
class ReferenceReplayTest {

    /** One recorded scenario, as {@code scenarios.json} lists it. */
    public record Scenario(JsonObject json) {

        String map() {
            return json.get("map").getAsString();
        }

        String resultFile() {
            return json.get("nn").getAsString() + "-" + json.get("action").getAsString() + ".mm";
        }

        @Override
        public String toString() {
            return map() + " " + resultFile();
        }
    }

    public static List<Scenario> scenarios() {
        try {
            String json = Files.readString(FreeMindAsserts.referenceDirectory().resolve("scenarios.json"), UTF_8);
            List<Scenario> scenarios = new ArrayList<>();
            for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
                scenarios.add(new Scenario(element.getAsJsonObject()));
            }
            return scenarios;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void everyScenarioMatchesItsRecordedResult(Scenario scenario) throws Exception {
        byte[] original = Files.readAllBytes(FreeMindAsserts.example(scenario.map()));
        byte[] expected = Files.readAllBytes(FreeMindAsserts.reference(stem(scenario.map()), scenario.resultFile()));

        assertArrayEquals(expected, replay(original, scenario), scenario.toString());
    }

    /** The same scenario on the map saved with Windows separators gives the recorded result with them. */
    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void aCrlfMapKeepsItsSeparators(Scenario scenario) throws Exception {
        byte[] original = crlf(Files.readAllBytes(FreeMindAsserts.example(scenario.map())));
        byte[] expected = crlf(Files.readAllBytes(FreeMindAsserts.reference(stem(scenario.map()), scenario.resultFile())));

        assertArrayEquals(expected, replay(original, scenario), scenario.toString());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("etalii.adp.freemind.FreeMindAsserts#examples")
    void everyExampleParsesAndWritesBackUnchanged(Path example) throws Exception {
        byte[] original = Files.readAllBytes(example);
        String separator = FreeMindConventions.detectLineSeparator(new String(original, UTF_8));
        String document = toDocument(new String(original, UTF_8));

        assertNotNull(MindMapParser.parse(document));
        assertArrayEquals(original, fromDocument(document, separator).getBytes(UTF_8));
    }

    private static byte[] replay(byte[] original, Scenario scenario) throws Exception {
        String file = new String(original, UTF_8);
        String separator = FreeMindConventions.detectLineSeparator(file);
        MindMap map = MindMapParser.parse(toDocument(file));
        Edit edit = edit(map, scenario.json());
        assertNotNull(edit, scenario.toString());
        assertEquals(label(scenario.json()), edit.label());
        return fromDocument(edit.changes().applyTo(map.text()), separator).getBytes(UTF_8);
    }

    static Edit edit(MindMap map, JsonObject s) {
        long now = s.get("now").getAsLong();
        Random random = new Random(s.get("seed").getAsLong());
        String action = s.get("action").getAsString();
        return switch (action) {
        case "add-child", "add-child-root", "add-child-leaf" -> MindMapEdits.addChild(map, key(s, "node"), text(s), now, random);
        case "add-sibling" -> MindMapEdits.addSibling(map, key(s, "node"), text(s), now, random);
        case "rename", "rename-rich" -> MindMapEdits.rename(map, key(s, "node"), text(s), now);
        case "delete", "delete-several" -> MindMapEdits.delete(map, keys(s));
        case "move-up", "move-down", "indent", "outdent", "drag-to-first-level", "drag-from-first-level" ->
            MindMapEdits.move(map, key(s, "node"), key(s, "target"), Placement.valueOf(s.get("placement").getAsString()));
        case "fold" -> MindMapEdits.setFolded(map, key(s, "node"), true);
        case "unfold" -> MindMapEdits.setFolded(map, key(s, "node"), false);
        default -> throw new IllegalArgumentException("Unknown action " + action);
        };
    }

    private static String label(JsonObject s) {
        return switch (s.get("action").getAsString()) {
        case "add-child", "add-child-root", "add-child-leaf" -> MindMapEdits.ADD_CHILD;
        case "add-sibling" -> MindMapEdits.ADD_SIBLING;
        case "rename", "rename-rich" -> MindMapEdits.RENAME;
        case "delete" -> MindMapEdits.DELETE_NODE;
        case "delete-several" -> MindMapEdits.DELETE_NODES;
        case "fold" -> MindMapEdits.FOLD;
        case "unfold" -> MindMapEdits.UNFOLD;
        default -> MindMapEdits.MOVE;
        };
    }

    /** A key as {@code scenarios.json} writes it: {@code id:<ID>} or {@code path:<index path>}. */
    public static NodeKey key(String written) {
        return written.startsWith("id:") ? NodeKey.ofId(written.substring(3)) : NodeKey.ofPath(written.substring(5));
    }

    private static NodeKey key(JsonObject s, String field) {
        return key(s.get(field).getAsString());
    }

    private static List<NodeKey> keys(JsonObject s) {
        List<NodeKey> keys = new ArrayList<>();
        s.getAsJsonArray("nodes").forEach(k -> keys.add(key(k.getAsString())));
        return keys;
    }

    private static String text(JsonObject s) {
        return s.get("text").getAsString();
    }

    private static String stem(String map) {
        return map.substring(0, map.length() - ".mm".length());
    }

    /** As the platform's document holds a file: every separator is {@code \n}. */
    static String toDocument(String file) {
        return file.replace("\r\n", "\n").replace('\r', '\n');
    }

    /** As the platform writes a document back: with the file's own separator. */
    static String fromDocument(String document, String separator) {
        return document.replace("\n", separator);
    }

    private static byte[] crlf(byte[] lf) {
        return new String(lf, UTF_8).replace("\n", "\r\n").getBytes(UTF_8);
    }
}
