package etalii.adp.core.diagram.edit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.intellij.openapi.util.UserDataHolderBase;

import etalii.adp.core.FormatProblem;
import etalii.adp.core.TextChanges;
import etalii.adp.core.diagram.AddRequest;
import etalii.adp.core.diagram.BoundsChange;
import etalii.adp.core.diagram.DiagramDefinition;
import etalii.adp.core.diagram.DiagramMapping;
import etalii.adp.core.diagram.DiagramRules;
import etalii.adp.core.diagram.EndSide;
import etalii.adp.core.diagram.Verdict;
import etalii.adp.core.diagram.model.Diagram;
import etalii.adp.core.diagram.model.End;
import etalii.adp.core.diagram.sample.SampleDefinition;
import etalii.adp.core.diagram.sample.SampleFiles;
import etalii.adp.core.diagram.sample.SampleMapping;

class DiagramCommandsTest {

    /** A designer without an IDE: the text in memory, re-read after every command, and a log of what ran. */
    static final class FakeHost extends UserDataHolderBase implements DiagramCommands.Host {

        final List<String> log;
        final List<String> labels = new ArrayList<>();
        final DiagramDefinition definition;
        final DiagramMapping mapping;
        String text;
        Diagram diagram;
        List<Object> selection = List.of();
        Point2D scroll = new Point2D.Double();

        FakeHost(String text) throws FormatProblem {
            this(text, SampleDefinition.DEFINITION, new SampleMapping(), new ArrayList<>());
        }

        FakeHost(String text, DiagramDefinition definition, DiagramMapping mapping, List<String> log) throws FormatProblem {
            this.text = text;
            this.definition = definition;
            this.log = log;
            this.mapping = new SpyMapping(mapping, log);
            this.diagram = mapping.read(text);
        }

        @Override
        public DiagramDefinition definition() {
            return definition;
        }

        @Override
        public DiagramMapping mapping() {
            return mapping;
        }

        @Override
        public Diagram diagram() {
            return diagram;
        }

        @Override
        public CharSequence text() {
            return text;
        }

        @Override
        public void execute(String label, TextChanges changes, Runnable reselect) {
            labels.add(label);
            text = changes.applyTo(text);
            try {
                diagram = mapping.read(text);
            } catch (FormatProblem problem) {
                throw new AssertionError(problem);
            }
            reselect.run();
        }

        @Override
        public void select(Collection<?> keys) {
            selection = List.copyOf(keys);
        }

        @Override
        public Point2D toViewport(Point2D diagramPoint) {
            return new Point2D.Double(diagramPoint.getX() - scroll.getX(), diagramPoint.getY() - scroll.getY());
        }
    }

    /** Records each mapping call in the shared log, then delegates. */
    record SpyMapping(DiagramMapping delegate, List<String> log) implements DiagramMapping {

        @Override
        public Diagram read(CharSequence text) throws FormatProblem {
            return delegate.read(text);
        }

        @Override
        public TextChanges add(CharSequence text, Diagram diagram, AddRequest request) {
            log.add("mapping.add");
            return delegate.add(text, diagram, request);
        }

        @Override
        public TextChanges remove(CharSequence text, Diagram diagram, Set<Object> keys) {
            log.add("mapping.remove " + keys.stream().map(String::valueOf).sorted().toList());
            return delegate.remove(text, diagram, keys);
        }

        @Override
        public TextChanges setBounds(CharSequence text, Diagram diagram, List<BoundsChange> changes) {
            log.add("mapping.setBounds");
            return delegate.setBounds(text, diagram, changes);
        }

        @Override
        public TextChanges connect(CharSequence text, Diagram diagram, String connectionType, End source, End target) {
            log.add("mapping.connect");
            return delegate.connect(text, diagram, connectionType, source, target);
        }

        @Override
        public TextChanges reconnect(CharSequence text, Diagram diagram, Object connection, EndSide side, End end) {
            log.add("mapping.reconnect");
            return delegate.reconnect(text, diagram, connection, side, end);
        }

        @Override
        public TextChanges setProperty(CharSequence text, Diagram diagram, Set<Object> keys, String property, String value) {
            log.add("mapping.setProperty");
            return delegate.setProperty(text, diagram, keys, property, value);
        }
    }

    /** Rules that log each call and allow everything. */
    record SpyRules(List<String> log) implements DiagramRules {

        @Override
        public Verdict canAdd(Diagram d, String type, Object targetOrSector) {
            log.add("rules.canAdd");
            return Verdict.allow();
        }

        @Override
        public Verdict canRemove(Diagram d, Set<Object> keys) {
            log.add("rules.canRemove");
            return Verdict.allow();
        }

        @Override
        public Verdict canConnect(Diagram d, String type, End source, End target) {
            log.add("rules.canConnect");
            return Verdict.allow();
        }
    }

    private static FakeHost host(String sample) throws Exception {
        return new FakeHost(SampleFiles.read(sample));
    }

    /** A host whose rules and mapping write to one log. */
    private static FakeHost spied(String sample) throws Exception {
        List<String> log = new ArrayList<>();
        DiagramDefinition definition = SampleDefinition.builder().rules(new SpyRules(log)).build();
        return new FakeHost(SampleFiles.read(sample), definition, new SampleMapping(), log);
    }

    @Test
    void permissionsComeBeforeRulesAndTheMapping() throws Exception {
        FakeHost host = spied("two-tasks.adpsample");
        DiagramCommands commands = new DiagramCommands(host);
        String before = host.text;
        Verdict verdict = commands.connect("flow", new End("b", "in"), new End("a", "out"));
        assertEquals(Verdict.refuse("'flow' may not start at anchor 'in' of 'task'"), verdict);
        assertEquals(List.of(), host.log);
        assertEquals(before, host.text);
        assertEquals(List.of(), host.labels);
        assertEquals(verdict, DiagramCommands.lastRefusal(host));
    }

    @Test
    void rulesComeBeforeTheMapping() throws Exception {
        FakeHost host = host("two-tasks.adpsample");
        String before = host.text;
        Verdict verdict = new DiagramCommands(host).remove(List.of("a", "b"));
        assertEquals(Verdict.refuse("a diagram needs at least one task"), verdict);
        assertEquals(List.of(), host.log);
        assertEquals(before, host.text);
    }

    @Test
    void anAllowedGestureAsksTheRulesThenTheMappingThenExecutes() throws Exception {
        FakeHost host = spied("two-tasks.adpsample");
        new DiagramCommands(host).connect("flow", new End("a", "out"), new End("b", "in"));
        assertEquals(List.of("rules.canConnect", "mapping.connect"), host.log);
        assertEquals(List.of("Connect Flow"), host.labels);
        assertEquals(2, host.diagram.connections().size());
        assertEquals(1, host.selection.size());
        assertTrue(host.diagram.connections().containsKey(host.selection.get(0)));
        assertNull(DiagramCommands.lastRefusal(host));
    }

    @Test
    void deletingAnElementIncludesItsConnections() throws Exception {
        FakeHost host = host("two-tasks.adpsample");
        assertEquals(Verdict.allow(), new DiagramCommands(host).remove(List.of("a")));
        assertEquals(List.of("mapping.remove [a, f1]"), host.log);
        assertEquals(List.of("Delete"), host.labels);
        assertEquals(Set.of("b"), host.diagram.elements().keySet());
        assertEquals(Map.of(), host.diagram.connections());
    }

    @Test
    void aMultiItemPropertyEditIsOneCommand() throws Exception {
        FakeHost host = host("two-tasks.adpsample");
        host.select(List.of("a", "b"));
        new DiagramCommands(host).setProperty(List.of("a", "b"), "owner", "Bob");
        assertEquals(List.of("Change Owner"), host.labels);
        assertEquals("Bob", host.diagram.element("a").properties().get("owner"));
        assertEquals("Bob", host.diagram.element("b").properties().get("owner"));
        assertEquals(List.of("a", "b"), host.selection);
    }

    @Test
    void commandLabelsAreTheContracts() throws Exception {
        FakeHost host = host("two-tasks.adpsample");
        DiagramCommands commands = new DiagramCommands(host);
        commands.add("task", new Point2D.Double(400, 40), null);
        commands.connect("flow", new End("b", "out"), new End("a", "in"));
        Object added = host.selection.get(0);
        commands.reconnect(added, EndSide.TARGET, new End("b", "in"));
        commands.move(Map.of("a", new Rectangle2D.Double(50, 50, 120, 60)));
        commands.resize("a", new Rectangle2D.Double(50, 50, 160, 60));
        commands.setProperty(List.of("a"), "priority", "low");
        commands.remove(List.of("b"));
        assertEquals(List.of("Add Task", "Connect Flow", "Reconnect Flow", "Move", "Resize", "Change Priority", "Delete"), host.labels);
    }

    @Test
    void anAddedElementIsSelectedAndGetsTheTypeDefaults() throws Exception {
        FakeHost host = host("two-tasks.adpsample");
        new DiagramCommands(host).add("decision", new Point2D.Double(400, 40), null);
        assertEquals(1, host.selection.size());
        var element = host.diagram.element(host.selection.get(0));
        assertEquals("decision", element.type());
        assertEquals(new Rectangle2D.Double(400, 40, 100, 60), element.bounds());
    }

    @Test
    void theSectorComesFromTheElementsCentreInDiagramSpace() throws Exception {
        FakeHost host = host("lanes.adpsample");
        new DiagramCommands(host).move(Map.of("a", new Rectangle2D.Double(40, 240, 120, 60)));
        assertEquals(List.of("Move to Shop"), host.labels);
        assertEquals("l2", host.diagram.element("a").sector());
    }

    @Test
    void aViewSpaceSectorIsResolvedInViewportCoordinates() throws Exception {
        FakeHost host = host("legend-view-space.adpsample");
        host.scroll = new Point2D.Double(100, 0);
        new DiagramCommands(host).move(Map.of("a", new Rectangle2D.Double(180, 40, 100, 50)));
        assertEquals("g", host.diagram.element("a").sector());
        assertEquals(List.of("Move to Legend"), host.labels);
        new DiagramCommands(host).move(Map.of("a", new Rectangle2D.Double(400, 40, 100, 50)));
        assertNull(host.diagram.element("a").sector());
        assertEquals("Move", host.labels.get(1));
    }

    @Test
    void refusalsByTheDefinition() throws Exception {
        FakeHost host = host("unknown-type.adpsample");
        DiagramCommands commands = new DiagramCommands(host);
        assertEquals(Verdict.refuse("unknown type is kept as it is"), commands.remove(List.of("z")));
        assertEquals(Verdict.refuse("unknown type is kept as it is"), commands.move(Map.of("z", new Rectangle2D.Double(0, 0, 80, 40))));
        assertEquals(Verdict.refuse("'Id' is read-only"), commands.setProperty(List.of("a"), "id", "x"));
        assertEquals(Verdict.refuse("'abc' is not a whole number"), commands.setProperty(List.of("a"), "estimate", "abc"));
        assertEquals(Verdict.refuse("anchor 'top' of 'task' does not accept 'flow'"),
                commands.connect("flow", new End("a", "top"), new End("a", "in")));
        assertEquals(List.of(), host.labels);

        FakeHost invisible = host("invisible-anchors.adpsample");
        DiagramCommands onDecision = new DiagramCommands(invisible);
        assertEquals(Verdict.refuse("'Decision' cannot be resized"), onDecision.resize("d", new Rectangle2D.Double(240, 40, 200, 60)));
        assertEquals(Verdict.refuse("'Task' can only be resized horizontally"),
                new DiagramCommands(host("two-tasks.adpsample")).resize("a", new Rectangle2D.Double(40, 40, 120, 90)));
        assertEquals(Verdict.refuse("'flow' may not end at anchor 'out' of 'decision'"),
                onDecision.connect("flow", new End("a", "out"), new End("d", "out")));
    }

    @Test
    void theLastRefusalIsRecordedAndClearedByTheNextAllowedGesture() throws Exception {
        FakeHost host = host("two-tasks.adpsample");
        DiagramCommands commands = new DiagramCommands(host);
        commands.remove(List.of("a", "b"));
        assertEquals("a diagram needs at least one task", DiagramCommands.lastRefusal(host).reason());
        commands.move(Map.of("a", new Rectangle2D.Double(60, 40, 120, 60)));
        assertNull(DiagramCommands.lastRefusal(host));
        assertFalse(host.labels.isEmpty());
    }
}
