package etalii.adp.freemind.edit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.jface.text.Document;
import org.junit.jupiter.api.Test;

import etalii.adp.freemind.MindMapAsserts;
import etalii.adp.freemind.edit.MindMapEdits.Edit;
import etalii.adp.freemind.edit.MindMapEdits.Placement;
import etalii.adp.freemind.model.MindMap;
import etalii.adp.freemind.model.NodeKey;
import etalii.adp.freemind.parse.MindMapParser;

/**
 * Records the reference results (spec 002, research R10): every scenario in
 * {@code freemind/testdata/reference/scenarios.json} run through this implementation's parser, edit
 * layer and text-edit application, with the scenario's seed and clock. The bytes go to
 * {@code freemind/testdata/reference/<map>/<nn>-<action>.mm}.
 */
public class ReferenceRecorder {

    private static final Pattern FIELD = Pattern.compile("\"(\\w+)\": (\"(?:[^\"\\\\]|\\\\.)*\"|\\d+|\\[[^\\]]*\\])");
    private static final Pattern STRING = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

    @Test
    public void record() throws Exception {
        Path repository = MindMapAsserts.example("freemind-1.0.1-rich-notes.mm").getParent().getParent().getParent().getParent();
        Path reference = repository.resolve("freemind/testdata/reference");
        int written = 0;
        for (String line : Files.readAllLines(reference.resolve("scenarios.json"), UTF_8)) {
            if (!line.trim().startsWith("{")) {
                continue;
            }
            Map<String, String> scenario = new LinkedHashMap<>();
            Matcher field = FIELD.matcher(line);
            while (field.find()) {
                scenario.put(field.group(1), field.group(2));
            }
            String mapName = string(scenario.get("map"));
            MindMap map = MindMapParser.parse(MindMapAsserts.read(MindMapAsserts.example(mapName)));
            Edit edit = edit(map, scenario);
            assertNotNull(edit, () -> "No edit for " + line);
            Document document = new Document(map.text());
            edit.textEdit().apply(document);
            Path out = reference.resolve(mapName.substring(0, mapName.length() - 3))
                    .resolve(string(scenario.get("nn")) + "-" + string(scenario.get("action")) + ".mm");
            Files.createDirectories(out.getParent());
            Files.write(out, document.get().getBytes(UTF_8));
            written++;
        }
        System.out.println("Recorded " + written + " reference results in " + reference);
    }

    private static Edit edit(MindMap map, Map<String, String> s) {
        long now = Long.parseLong(s.get("now"));
        Random random = new Random(Long.parseLong(s.get("seed")));
        String action = string(s.get("action"));
        return switch (action) {
        case "add-child", "add-child-root", "add-child-leaf" -> MindMapEdits.addChild(map, key(s.get("node")), string(s.get("text")), now, random);
        case "add-sibling" -> MindMapEdits.addSibling(map, key(s.get("node")), string(s.get("text")), now, random);
        case "rename", "rename-rich" -> MindMapEdits.rename(map, key(s.get("node")), string(s.get("text")), now);
        case "delete", "delete-several" -> MindMapEdits.delete(map, keys(s.get("nodes")));
        case "move-up", "move-down", "indent", "outdent", "drag-to-first-level", "drag-from-first-level" ->
            MindMapEdits.move(map, key(s.get("node")), key(s.get("target")), Placement.valueOf(string(s.get("placement"))));
        case "fold" -> MindMapEdits.setFolded(map, key(s.get("node")), true);
        case "unfold" -> MindMapEdits.setFolded(map, key(s.get("node")), false);
        default -> throw new IllegalArgumentException("Unknown action " + action);
        };
    }

    private static NodeKey key(String json) {
        String key = string(json);
        return key.startsWith("id:") ? NodeKey.ofId(key.substring(3)) : NodeKey.ofPath(key.substring(5));
    }

    private static List<NodeKey> keys(String json) {
        List<NodeKey> keys = new ArrayList<>();
        Matcher item = STRING.matcher(json);
        while (item.find()) {
            keys.add(key(item.group()));
        }
        return keys;
    }

    /** A JSON string literal's value; the generator escapes only quotes, backslashes and non-ASCII. */
    private static String string(String json) {
        String body = json.substring(1, json.length() - 1);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c != '\\') {
                out.append(c);
            } else if (body.charAt(++i) == 'u') {
                out.append((char) Integer.parseInt(body.substring(i + 1, i + 5), 16));
                i += 4;
            } else {
                out.append(body.charAt(i));
            }
        }
        return out.toString();
    }
}
