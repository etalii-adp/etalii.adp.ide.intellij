package etalii.adp.fbl.real;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import etalii.adp.core.FormatProblem;
import etalii.adp.fbl.FblElement;
import etalii.adp.fbl.FblModel;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.history.OpenBody;
import etalii.adp.fbl.real.RealFileCorpus.CorpusBinding;
import etalii.adp.freemind.model.MapNode;
import etalii.adp.freemind.parse.MindMapParser;

/**
 * For the one module this host has (spec 010, FR-019): the ids the {@code node} rule of the mind
 * map binding reads from each of the repository's maps are the ids the FreeMind module's own
 * parser reads, node for node in document order. A file one side reads and the other refuses, and
 * every difference in the ids, is a listed divergence. Only public API of the module is used, and
 * nothing in it is changed. The counterpart of standalone's
 * {@code RealFiles/ModuleCrossCheck.Tests.cs}, whose other three modules this host does not have.
 */
class FreeMindCrossCheckTest {

    private static final CorpusBinding MAPS = RealFileCorpus.find("mindmap");

    /** The rule of the binding whose elements are the map's nodes. */
    private static final String NODE_RULE = "node";

    static Stream<String> maps() {
        return RealFileCorpus.files(MAPS).stream();
    }

    @ParameterizedTest
    @MethodSource("maps")
    void theBindingReadsTheIdsTheModuleReads(String file) {
        // Arrange.
        byte[] bytes = RealFileCorpus.bytes(file);
        FblBinding binding = RealFileCorpus.binding(MAPS);

        // Act.
        FblModel model = OpenBody.open(bytes, binding, RealFileCorpus.options(binding, file)).model();
        List<String> module = null;
        String refused = null;
        try {
            module = new ArrayList<>();
            collect(MindMapParser.parse(new String(bytes, UTF_8)).root(), module);
        } catch (FormatProblem problem) {
            module = null;
            refused = String.valueOf(problem.getMessage());
        }

        // Assert.
        Divergences.check("cross-check", MAPS.reference(), file, difference(model, module, refused));
    }

    /** The ids of a node and the nodes under it in document order; null for a node without an {@code ID}. */
    private static void collect(MapNode node, List<String> ids) {
        ids.add(node.id());
        for (MapNode child : node.children()) {
            collect(child, ids);
        }
    }

    /** The ids the binding's node rule reads, in document order; null for a node whose id is not stored in the map. */
    private static List<String> ofBinding(FblModel model) {
        List<String> ids = new ArrayList<>();
        for (FblElement element : model.elements()) {
            if (!element.isRelation() && NODE_RULE.equals(element.rule())) {
                ids.add(element.idIsStored() ? element.id() : null);
            }
        }
        return ids;
    }

    /**
     * The disagreement between the two readings, or null when they agree.
     *
     * @param module the ids the module's parser reads, or null when it refuses the file
     * @param refused the module's reason when it refuses the file, else null
     */
    private static String difference(FblModel model, List<String> module, String refused) {
        if (model.unreadable()) {
            return module == null ? null : "the binding finds the map unreadable; the module reads " + module.size() + " nodes";
        }
        List<String> fbl = ofBinding(model);
        if (module == null) {
            return "the module refuses the map (" + refused + "); the binding reads " + fbl.size() + " nodes";
        }
        if (fbl.equals(module)) {
            return null;
        }
        int at = 0;
        while (at < fbl.size() && at < module.size() && Objects.equals(fbl.get(at), module.get(at))) {
            at++;
        }
        return "Node: the binding reads " + fbl.size() + " and the module " + module.size() + "; they first differ at node " + (at + 1)
                + ", where the binding reads " + name(fbl, at) + " and the module " + name(module, at);
    }

    private static String name(List<String> ids, int index) {
        return index >= ids.size() ? "nothing" : ids.get(index) == null ? "a node without an id" : ids.get(index);
    }
}
