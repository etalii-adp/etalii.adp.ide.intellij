package etalii.adp.fbl.support;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import etalii.adp.fbl.FblElement;
import etalii.adp.fbl.Splice;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.plugin.PersistencePlugin;
import etalii.adp.fbl.plugin.PluginPlanRequest;
import etalii.adp.fbl.plugin.PluginPlanResult;
import etalii.adp.fbl.plugin.PluginReadRequest;
import etalii.adp.fbl.plugin.PluginReadResult;
import etalii.adp.fbl.plugin.PluginSplice;
import etalii.adp.fbl.plugin.PluginTemplateRequest;
import etalii.adp.fbl.text.Span;

/**
 * A fake persistence plugin: every line {@code label <text>} is an element {@code line<n>}
 * with a writable {@code label}; it plans label changes and refuses everything else.
 */
public final class FakePlugin implements PersistencePlugin {

    private final String id;
    private int plans;

    public FakePlugin(String id) {
        this.id = id;
    }

    @Override
    public String id() {
        return id;
    }

    /** How many times the plugin was asked to plan. */
    public int plans() {
        return plans;
    }

    @Override
    public PluginReadResult read(PluginReadRequest request) {
        byte[] bytes = request.files().get(0).bytes();
        List<FblElement> elements = new ArrayList<>();
        int start = 0;
        int number = 0;
        for (int i = 0; i <= bytes.length; i++) {
            if (i < bytes.length && bytes[i] != '\n') {
                continue;
            }
            if (i > start) {
                number++;
                String text = new String(bytes, start, i - start, UTF_8);
                elements.add(new FblElement("line" + number, true, "Line", "line", false, Map.<String, Object>of("label", text.substring(6)),
                        null, null, null, null, new Span(start, i), number));
            }
            start = i + 1;
        }
        return new PluginReadResult(elements, List.of(), false);
    }

    @Override
    public PluginPlanResult plan(PluginPlanRequest request) {
        plans++;
        if (!(request.change() instanceof ModelChange.Set set)) {
            return new PluginPlanResult.Refused("The fake plugin removes nothing.");
        }
        FblElement element = find(request.last(), set.id());
        int start = element.ownSpan().start() + 6;
        return new PluginPlanResult.Planned(List.of(
                new PluginSplice("", new Splice(SpliceOperation.REPLACE_VALUE, start, element.ownSpan().end(), (String) set.attributes().get("label")))));
    }

    @Override
    public byte[] template(PluginTemplateRequest request) {
        return ("template for " + request.name() + " (" + request.placeholders().get("base") + ")").getBytes(UTF_8);
    }

    @Override
    public List<String> watch(PluginReadResult last) {
        return List.of();
    }

    /** The element of that id in a reading, or null. */
    public static FblElement find(PluginReadResult result, String id) {
        return result.elements().stream().filter(e -> e.id().equals(id)).findFirst().orElse(null);
    }
}
