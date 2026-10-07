package etalii.adp.fbl.real;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import etalii.adp.fbl.support.Corpus;

/**
 * {@code fbl/testdata/divergences.json}: every disagreement the real-file tests found between a
 * copied binding and a real file, recorded instead of hidden (spec 010, FR-022). A disagreement
 * that is not listed fails its test; a listed one whose observation has changed, or that no longer
 * occurs, fails too, so the list cannot go stale. The counterpart of standalone's
 * {@code RealFiles/Divergences.cs}.
 */
final class Divergences {

    /** The properties a divergence can be of: standalone's, and the cross-check with this host's own module. */
    static final Set<String> PROPERTIES = Set.of("unreadable", "edit", "remove", "registration-body", "registration-view", "registration-resource",
            "registration-layout", "reading-suggest", "cross-check");

    private static final Set<String> KEYS = Set.of("property", "binding", "file", "observed", "reason");

    private Divergences() {
    }

    /**
     * A place where a copied binding and a real file disagree: the property it breaks, the binding
     * and file, what was observed exactly, and why it is so.
     */
    record Divergence(String property, String binding, String file, String observed, String reason) {
    }

    /** Loaded once, on first use. */
    private static final class Holder {

        static final List<Divergence> ALL = load();
    }

    static List<Divergence> all() {
        return Holder.ALL;
    }

    static Path filePath() {
        return Corpus.root().resolve("divergences.json");
    }

    /**
     * Checks one property on one file: {@code observed} is null when the binding and the file agree,
     * else the exact disagreement, which must then be listed with that observation.
     */
    static void check(String property, String binding, String file, String observed) {
        assertTrue(PROPERTIES.contains(property), () -> "'" + property + "' is not a property a divergence is recorded of.");
        Divergence listed = all().stream().filter(d -> d.property().equals(property) && d.binding().equals(binding) && d.file().equals(file))
                .findFirst().orElse(null);
        if (observed == null) {
            assertTrue(listed == null,
                    () -> "The divergence listed for " + property + " on " + file + " (" + binding + ") no longer occurs; remove it from divergences.json.");
            return;
        }
        assertTrue(listed != null, () -> property + " diverges on " + file + " (" + binding + ") and is not listed in divergences.json:\n" + observed);
        assertTrue(listed.observed().equals(observed), () -> "The divergence of " + property + " on " + file + " (" + binding + ") has changed.\nListed:   "
                + listed.observed() + "\nObserved: " + observed);
    }

    private static List<Divergence> load() {
        JsonElement root = JsonParser.parseString(new String(Corpus.bytes(filePath()), UTF_8));
        assertTrue(root.isJsonArray(), "divergences.json is a JSON array.");
        List<Divergence> all = new ArrayList<>();
        for (JsonElement element : root.getAsJsonArray()) {
            assertTrue(element.isJsonObject(), () -> "An entry of divergences.json is not an object: " + element);
            JsonObject entry = element.getAsJsonObject();
            assertEquals(KEYS, entry.keySet(), () -> "An entry of divergences.json has other keys than the five: " + entry);
            all.add(new Divergence(text(entry, "property"), text(entry, "binding"), text(entry, "file"), text(entry, "observed"), text(entry, "reason")));
        }
        return List.copyOf(all);
    }

    private static String text(JsonObject entry, String key) {
        JsonElement value = entry.get(key);
        assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(), () -> "'" + key + "' is not a string in " + entry);
        return value.getAsString();
    }
}
