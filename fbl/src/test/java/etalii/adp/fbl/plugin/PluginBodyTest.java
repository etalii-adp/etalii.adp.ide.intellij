package etalii.adp.fbl.plugin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Map;

import org.junit.jupiter.api.Test;

import etalii.adp.fbl.Finding;
import etalii.adp.fbl.FindingCodes;
import etalii.adp.fbl.history.SplicedFile;
import etalii.adp.fbl.history.UndoResult;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.plan.PlanResult;
import etalii.adp.fbl.support.CorpusBindings;
import etalii.adp.fbl.support.FakePlugin;

/**
 * The host side of the plugin contract (FBL §11.3, §15.1), proved with a hand-written fake plugin,
 * since no plugin is implemented by this library. The counterparts of standalone's
 * {@code Plugins/PluginBody.Tests.cs}.
 */
class PluginBodyTest {

    private static final String TURTLE_ID = "net.etalii.adp.w3c.turtle";

    private static byte[] utf8(String text) {
        return text.getBytes(UTF_8);
    }

    @Test
    void aMissingPluginOpensTheBodyReadOnlyWithAFinding() {
        var turtle = CorpusBindings.binding("w3c-turtle.fbl", "turtle");

        PluginBody body = PluginBody.open(utf8("ex:a ex:b ex:c .\n"), turtle, null, "a.ttl");

        assertTrue(body.isReadOnly());
        assertEquals(1, body.model().findings().size());
        Finding finding = body.model().findings().get(0);
        assertEquals(FindingCodes.PLUGIN_MISSING, finding.code());
        assertTrue(finding.message().contains(TURTLE_ID), finding.message());
        assertInstanceOf(PlanResult.Refused.class, body.change(new ModelChange.Remove("x")));
        assertThrows(IllegalStateException.class, () -> body.save(bytes -> fail("A read-only body is never written.")));
    }

    @Test
    void aPluginWithAnotherIdCountsAsMissing() {
        PluginBody body = PluginBody.open(utf8("x\n"), CorpusBindings.binding("w3c-turtle.fbl", "turtle"), new FakePlugin("net.example.other"));

        assertTrue(body.isReadOnly());
        assertEquals(1, body.model().findings().size());
        assertEquals(FindingCodes.PLUGIN_MISSING, body.model().findings().get(0).code());
    }

    @Test
    void thePluginsSplicesAreAppliedRecordedAndUndone() {
        byte[] original = utf8("label a\nlabel b\n");
        PluginBody body = PluginBody.open(original, CorpusBindings.binding("w3c-turtle.fbl", "turtle"), new FakePlugin(TURTLE_ID));

        PlanResult result = body.change(new ModelChange.Set("line1", Map.<String, Object>of("label", "renamed")));

        // The host applied the plugin's splice and read again through the plugin.
        assertInstanceOf(PlanResult.Planned.class, result);
        assertEquals("label renamed\nlabel b\n", new String(body.bytes(), UTF_8));
        assertEquals("renamed", body.model().find("line1").attributes().get("label"));
        assertEquals(SplicedFile.DRIFT_UNDO, assertInstanceOf(UndoResult.Refused.class, body.undo(utf8("drifted"))).reason());
        assertInstanceOf(UndoResult.Done.class, body.undo());
        assertArrayEquals(original, body.bytes());
    }

    @Test
    void aPluginsRefusalWritesNothing() {
        byte[] original = utf8("label a\n");
        PluginBody body = PluginBody.open(original, CorpusBindings.binding("w3c-turtle.fbl", "turtle"), new FakePlugin(TURTLE_ID));

        PlanResult result = body.change(new ModelChange.Remove("line1"));

        assertEquals("The fake plugin removes nothing.", assertInstanceOf(PlanResult.Refused.class, result).reason());
        assertArrayEquals(original, body.bytes());
    }

    @Test
    void aReadOnlyBindingsPluginIsNeverAskedToPlan() {
        // The Helm chart binding is read-only.
        FakePlugin plugin = new FakePlugin("net.etalii.adp.helm.chartFolder");
        PluginBody body = PluginBody.open(utf8("label a\n"), CorpusBindings.binding("helm-chart.fbl", "chart"), plugin);

        PlanResult result = body.change(new ModelChange.Set("line1", Map.<String, Object>of("label", "x")));

        assertInstanceOf(PlanResult.Refused.class, result);
        assertEquals(0, plugin.plans());
    }
}
