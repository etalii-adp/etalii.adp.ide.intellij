package etalii.adp.fbl.support;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.FblDocumentLoader;
import etalii.adp.fbl.document.ProblemSeverity;

/** The bindings of the conformance corpus, each loaded once and checked for load errors. */
public final class CorpusBindings {

    private static final Map<String, Map<String, FblBinding>> DOCUMENTS = new LinkedHashMap<>();

    private CorpusBindings() {
    }

    /** One binding of the corpus with the document it is in. */
    public record Named(String document, FblBinding binding) {
    }

    /** A binding by its document's file name and its name in that document. */
    public static synchronized FblBinding binding(String document, String name) {
        FblBinding binding = document(document).get(name);
        assertNotNull(binding, () -> document + " has no binding '" + name + "'");
        return binding;
    }

    /** Every binding of the corpus, the documents in ordinal order of file name and each one's bindings in document order. */
    public static synchronized List<Named> all() {
        List<Named> all = new ArrayList<>();
        for (String file : Corpus.files(Corpus.conformance())) {
            if (file.endsWith(".fbl") && !file.contains("/")) {
                document(file).values().forEach(binding -> all.add(new Named(file, binding)));
            }
        }
        return all;
    }

    private static Map<String, FblBinding> document(String document) {
        return DOCUMENTS.computeIfAbsent(document, name -> {
            FblDocumentLoader.Loaded loaded = FblDocumentLoader.load(Corpus.conformance().resolve(name));
            assertFalse(loaded.problems().stream().anyMatch(p -> p.severity() == ProblemSeverity.ERROR), () -> loaded.problems().toString());
            return loaded.document().bindings();
        });
    }
}
