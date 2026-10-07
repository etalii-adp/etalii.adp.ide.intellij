package etalii.adp.fbl.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import etalii.adp.fbl.family.json.JsonMember;
import etalii.adp.fbl.family.json.JsonParser;
import etalii.adp.fbl.family.json.JsonSyntaxException;
import etalii.adp.fbl.family.json.JsonValue;

/**
 * Loads an FBL document as FBL §14.1 says, apart from validating against the JSON Schema (left to
 * etalii.adp's own build): parse rejecting duplicate keys, check the version, map every construct,
 * resolve names, compile every regular expression and CEL expression, and check the rules. Every
 * problem is collected; a document with an error yields no document.
 */
public final class FblDocumentLoader {

    public static final int SUPPORTED_MAJOR = 0;

    private FblDocumentLoader() {
    }

    /** What loading gives: every problem found, and the document, which is null when one of them is an error. */
    public record Loaded(List<Problem> problems, FblDocument document) {

        public Loaded {
            problems = List.copyOf(problems);
        }
    }

    /** A binding a reference resolved to, with the problems (warnings) of the document it is in. */
    public record Resolved(FblBinding binding, List<Problem> problems) {
    }

    public static Loaded load(Path path) {
        try {
            return load(Files.readAllBytes(path), path.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Loads a document from its bytes. {@code path} is where they came from, or null. */
    public static Loaded load(byte[] json, String path) {
        List<Problem> problems = new ArrayList<>();
        JsonValue root;
        try {
            root = JsonParser.parseStrict(json, 0);
        } catch (JsonSyntaxException e) {
            problems.add(new Problem("", ProblemSeverity.ERROR, "The document is not JSON: " + e.getMessage()));
            return new Loaded(problems, null);
        }
        findDuplicateKeys(root, "", problems);
        if (!problems.isEmpty()) {
            return new Loaded(problems, null);
        }

        if (!root.isObject()) {
            problems.add(new Problem("", ProblemSeverity.ERROR, "An FBL document is a JSON object."));
            return new Loaded(problems, null);
        }
        String version = root.string("fbl");
        if (version == null) {
            problems.add(new Problem("/fbl", ProblemSeverity.ERROR, "The document does not say which FBL version it is written in."));
            return new Loaded(problems, null);
        }
        String[] parts = version.split("\\.", -1);
        Integer major = parts.length < 2 ? null : parseInt(parts[0]);
        Integer minor = parts.length < 2 ? null : parseInt(parts[1]);
        if (major == null || minor == null) {
            problems.add(new Problem("/fbl", ProblemSeverity.ERROR, "'" + version + "' is not an FBL version."));
            return new Loaded(problems, null);
        }
        if (major != SUPPORTED_MAJOR) {
            problems.add(new Problem("/fbl", ProblemSeverity.ERROR, "FBL " + version + " is not supported; this library reads FBL " + SUPPORTED_MAJOR + ".x."));
            return new Loaded(problems, null);
        }
        if (minor > 1) {
            problems.add(new Problem("/fbl", ProblemSeverity.WARNING, "FBL " + version + " is newer than 0.1; what this library does not know is ignored."));
        }
        JsonValue bindings = root.get("bindings");
        if (bindings == null || !bindings.isObject() || bindings.members().isEmpty()) {
            problems.add(new Problem("/bindings", ProblemSeverity.ERROR, "A document has at least one binding."));
            return new Loaded(problems, null);
        }

        Map<String, FblBinding> result = new LinkedHashMap<>();
        for (JsonMember property : bindings.members()) {
            String pointer = "/bindings/" + escape(property.name());
            if (!Names.isName(property.name())) {
                problems.add(new Problem(pointer, ProblemSeverity.ERROR, "'" + property.name() + "' is not a valid binding name."));
            }
            FblBinding binding = BindingReader.read(property.name(), property.value(), pointer, problems);
            if (binding != null) {
                BindingChecker.check(binding, pointer, problems);
                result.put(property.name(), binding);
            }
        }

        if (problems.stream().anyMatch(p -> p.severity() == ProblemSeverity.ERROR)) {
            return new Loaded(problems, null);
        }
        return new Loaded(problems, new FblDocument(version, path, result));
    }

    /** Resolves a binding reference, {@code doc.fbl#name} or {@code #name}, against {@code referrer} (FBL §2.3). */
    public static Resolved resolveReference(String reference, Path referrer) {
        int hash = reference.lastIndexOf('#');
        if (hash < 0) {
            throw new IllegalArgumentException("'" + reference + "' is not a binding reference.");
        }
        String file = reference.substring(0, hash);
        String name = reference.substring(hash + 1);
        Path folder = referrer.toAbsolutePath().getParent();
        Path path = file.isEmpty() ? referrer : (folder == null ? Path.of(file) : folder.resolve(file)).toAbsolutePath().normalize();
        Loaded loaded = load(path);
        if (loaded.document() == null) {
            throw new IllegalStateException("The FBL document '" + path + "' does not load: "
                    + String.join("; ", loaded.problems().stream().map(Problem::toString).toList()));
        }
        FblBinding binding = loaded.document().bindings().get(name);
        if (binding == null) {
            throw new IllegalStateException("The FBL document '" + path + "' has no binding '" + name + "'.");
        }
        return new Resolved(binding, loaded.problems());
    }

    /** A reference token of a JSON Pointer (RFC 6901). */
    static String escape(String token) {
        return token.replace("~", "~0").replace("/", "~1");
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.valueOf(text.strip());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void findDuplicateKeys(JsonValue value, String pointer, List<Problem> problems) {
        int index = 0;
        for (JsonMember member : value.members()) {
            String here = pointer + "/" + (value.isObject() ? escape(member.name()) : String.valueOf(index++));
            if (member.duplicate()) {
                problems.add(new Problem(here, ProblemSeverity.ERROR, "The key '" + member.name() + "' appears twice."));
            }
            findDuplicateKeys(member.value(), here, problems);
        }
    }
}
