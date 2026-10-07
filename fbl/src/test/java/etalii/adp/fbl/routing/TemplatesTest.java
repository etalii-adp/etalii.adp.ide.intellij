package etalii.adp.fbl.routing;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.fbl.FblModel;
import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.Finding;
import etalii.adp.fbl.FindingSeverity;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.history.OpenBody;
import etalii.adp.fbl.support.CorpusBindings;
import etalii.adp.fbl.support.FakePlugin;

/** Templates (FBL §13). The counterparts of standalone's {@code Routing/Templates.Tests.cs}. */
class TemplatesTest {

    /** The templates the copied bindings declare for a declared reader, one for each origin: 11 when the corpus was copied. */
    private static final int DECLARED_TEMPLATES = 11;

    static Stream<Arguments> declaredTemplates() {
        List<Arguments> data = new ArrayList<>();
        for (CorpusBindings.Named named : CorpusBindings.all()) {
            FblBinding binding = named.binding();
            if (binding.template() == null || binding.plugin() != null) {
                continue;
            }
            for (String origin : binding.claims().origins()) {
                data.add(Arguments.of(named.document(), binding.name() + "|" + origin));
            }
        }
        // A broken enumeration fails here rather than passing on no template at all.
        assertEquals(DECLARED_TEMPLATES, data.size(), "The templates the copied bindings declare");
        return data.stream();
    }

    @ParameterizedTest
    @MethodSource("declaredTemplates")
    void everyDeclaredTemplateReadsBackWithoutAWarning(String document, String bindingAndOrigin) {
        String name = bindingAndOrigin.split("\\|")[0];
        String origin = bindingAndOrigin.split("\\|")[1];
        FblBinding binding = CorpusBindings.binding(document, name);
        String extension = binding.claims().extensions().isEmpty() ? ".txt" : binding.claims().extensions().get(0);

        byte[] bytes = TemplateWriter.produce(binding, origin, "New plan" + extension, rule -> "ID_" + rule + "_1");
        FblModel model = OpenBody.open(bytes, binding, FblOptions.DEFAULT.withFileName("New plan" + extension)).model();

        assertFalse(model.unreadable(), document + "#" + name + " (" + origin + "): the template is unreadable.");
        List<Finding> warnings = model.findings().stream().filter(f -> f.severity().compareTo(FindingSeverity.WARNING) >= 0).toList();
        assertEquals(List.of(), warnings);
    }

    @Test
    void aTemplateByOriginWinsOverTheBindingsText() {
        FblBinding turtle = CorpusBindings.binding("w3c-turtle.fbl", "turtle");

        String skos = new String(TemplateWriter.produce(turtle, "w3c/skos", "Animals 2.ttl", rule -> "x"), UTF_8);
        String rdf = new String(TemplateWriter.produce(turtle, "w3c/rdf", "Animals 2.ttl", rule -> "x"), UTF_8);

        assertTrue(skos.contains("ex:Animals_2 a skos:ConceptScheme ;\r\n"), skos);
        assertTrue(skos.contains("skos:prefLabel \"Animals 2\"@en"), skos);
        assertTrue(rdf.contains("ex:Animals_2 rdfs:label \"Animals 2\" .\r\n"), rdf);
    }

    @ParameterizedTest
    @CsvSource({
            "Plan, Plan",
            "my plan-2, my_plan_2",
            "2024 roadmap, _2024_roadmap",
            "__x__, x",
            "---, untitled",
            "café, caf",
    })
    void theKeyPlaceholderIsSanitisedExactly(String baseName, String key) {
        assertEquals(key, TemplateWriter.key(baseName));
    }

    @Test
    void onlyTheFourPlaceholdersAreReplaced() {
        String text = TemplateWriter.replace("{name} {base} {key} {newid:node} {id} {newid} { base }", "a b.mm", rule -> "N-" + rule);

        assertEquals("a b.mm a b a_b N-node {id} {newid} { base }", text);
    }

    @Test
    void aPluginWithoutTemplateTextIsAskedForOne() {
        FblBinding chart = CorpusBindings.binding("helm-chart.fbl", "chart");
        FakePlugin plugin = new FakePlugin("net.etalii.adp.helm.chartFolder");

        byte[] bytes = TemplateWriter.produce(chart, "helm/chart", "web.yaml", rule -> "x", plugin);

        assertArrayEquals("template for web.yaml (web)".getBytes(UTF_8), bytes);
    }
}
