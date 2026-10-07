package etalii.adp.fbl.family.yaml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.FindingCodes;
import etalii.adp.fbl.FindingSeverity;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.document.AttributeBinding;
import etalii.adp.fbl.document.CreateContainer;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.InsertSettings;
import etalii.adp.fbl.document.Rule;
import etalii.adp.fbl.document.Slot;
import etalii.adp.fbl.plan.NewText;
import etalii.adp.fbl.plan.Plan;
import etalii.adp.fbl.rule.Entry;
import etalii.adp.fbl.rule.InsertRequest;
import etalii.adp.fbl.rule.ReadElement;
import etalii.adp.fbl.rule.SlotChange;
import etalii.adp.fbl.rule.SlotRead;
import etalii.adp.fbl.rule.TreeEntry;
import etalii.adp.fbl.rule.TreeFamily;
import etalii.adp.fbl.rule.TreeValue;
import etalii.adp.fbl.rule.Unreadable;
import etalii.adp.fbl.rule.ValueKind;
import etalii.adp.fbl.rule.ValueStyle;
import etalii.adp.fbl.text.BodyText;
import etalii.adp.fbl.text.Span;
import etalii.adp.fbl.text.TextLine;

/**
 * The {@code yaml} family (FBL §4.3): the {@link YamlParser} decides whether the first document is
 * well-formed and reads its structure and spans, and writing keeps each replaced scalar's style and
 * the body's indentation (FBL §6.3).
 */
public final class YamlFamily extends TreeFamily {

    private List<Span> leaves = new ArrayList<>();

    public YamlFamily(BodyText text, FblBinding binding, FblOptions options) {
        super(text, binding, options);
    }

    @Override
    public String familyName() {
        return "yaml";
    }

    @Override
    public List<Span> leaves() {
        return Collections.unmodifiableList(leaves);
    }

    @Override
    public void parse() {
        YamlParser parser = new YamlParser(text());
        try {
            TreeValue value = parser.parseDocument();
            TreeEntry root = TreeEntry.root(value.span(), 0, value);
            setRoot(root);
            index(root);
        } catch (YamlParser.YamlError error) {
            // A body that is not YAML where the parser stopped is unreadable (FBL §4.3, §7.5).
            setUnreadable(new Unreadable(error.offset(), error.malformed() ? "The body is not well-formed YAML: " + error.getMessage() : error.getMessage()));
            return;
        }
        leaves = parser.leaves();
        for (YamlParser.Duplicate duplicate : parser.duplicates()) {
            report(FindingCodes.DUPLICATE_KEY, FindingSeverity.WARNING, "The key '" + duplicate.name() + "' appears again in this mapping; only the first is read.",
                    duplicate.span());
        }
        for (Entry entry : allEntries) {
            if (entry instanceof TreeEntry tree && !tree.isRoot()) {
                tree.setLineSpan(lineSpanOf(tree));
            }
        }
    }

    /** Between leaves there is only whitespace, comments, indicators, anchors, tags and document markers. */
    @Override
    public boolean isTrivia(Span gap) {
        byte[] bytes = text().bytes();
        for (int i = gap.start(); i < gap.end(); i++) {
            byte b = bytes[i];
            if (b == ' ' || b == '\t' || b == '\r' || b == '\n' || b == ':' || b == '-' || b == '?' || b == ',' || b == '.') {
                continue;
            }
            if (b == '#' || b == '%') {
                while (i < gap.end() && !(bytes[i] == '\r' || bytes[i] == '\n')) {
                    i++;
                }
                continue;
            }
            if (b == '&' || b == '!') {
                while (i < gap.end() && !(bytes[i] == ' ' || bytes[i] == '\t' || bytes[i] == '\r' || bytes[i] == '\n')) {
                    i++;
                }
                continue;
            }
            return false;
        }
        return true;
    }

    /**
     * An entry's line span (FBL §4.1.1) when it starts and ends its lines: from its first line,
     * extended over the comment lines directly above it at its indentation, to the end of its last
     * line's ending, a trailing comment included. Null when the entry shares a line.
     */
    private Span lineSpanOf(TreeEntry entry) {
        if (!onlyWhitespaceBefore(entry.own().start())) {
            return null;
        }
        if (!onlyTriviaAfter(entry.own().end(), Byte.valueOf((byte) '#'))) {
            return null;
        }
        byte[] bytes = text().bytes();
        List<TextLine> lines = text().lines();
        int first = text().lineIndexAt(entry.own().start());
        int last = text().lineIndexAt(Math.max(entry.own().start(), entry.own().end() - 1));
        int start = lines.get(first).start();
        for (int above = first - 1; above >= 0; above--) {
            TextLine line = lines.get(above);
            int p = line.start();
            while (p < line.contentEnd() && bytes[p] == ' ') {
                p++;
            }
            if (p >= line.contentEnd() || bytes[p] != '#' || p - line.start() != entry.indent()) {
                break;
            }
            start = line.start();
        }
        return new Span(start, lines.get(last).end());
    }

    private int lineEndAfter(TreeEntry entry) {
        if (entry.lineSpan() != null) {
            return entry.lineSpan().end();
        }
        return text().lines().get(text().lineIndexAt(Math.max(entry.own().start(), entry.own().end() - 1))).end();
    }

    // ---- new text (FBL §6.3) ----

    @Override
    public String format(SlotRead read, AttributeBinding binding, Object value) {
        TreeEntry node = read.node() instanceof TreeEntry tree ? tree : null;
        TreeValue old = node == null ? null : node.value();
        String wire = binding == null ? null : NewText.wire(binding, value, read.wire());
        String written = scalar(wire != null ? wire : value, old, binding, node == null ? 0 : node.indent());
        return old != null && old.style() == ValueStyle.EMPTY ? " " + written : written;
    }

    private String scalar(Object value, TreeValue old, AttributeBinding binding, int keyIndent) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        if (value instanceof String s) {
            return string(s, old, binding, keyIndent);
        }
        if (value instanceof Iterable<?> list) {
            List<String> items = new ArrayList<>();
            for (Object item : list) {
                items.add(scalar(item, null, binding, keyIndent));
            }
            return "[" + String.join(", ", items) + "]";
        }
        Double number = NewText.tryNumber(value);
        return number != null ? NewText.number(number, binding == null ? null : binding.decimals()) : string(NewText.plain(value, binding), old, binding, keyIndent);
    }

    private String string(String value, TreeValue old, AttributeBinding binding, int keyIndent) {
        boolean timeTyped = binding != null && binding.keepTimePrecision();
        if (timeTyped && old != null && old.kind() == ValueKind.SCALAR && old.text().length() > 0) {
            String kept = NewText.keepPrecision(old.text(), value);
            if (kept != null) {
                value = kept;
            }
        }
        boolean multiline = value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        ValueStyle oldStyle = old == null ? null : old.style();
        if (oldStyle == ValueStyle.PLAIN && YamlScalars.isPlainSafe(value, timeTyped)) {
            return value;
        }
        if (oldStyle == ValueStyle.SINGLE && !multiline) {
            return YamlScalars.singleQuoted(value);
        }
        if (oldStyle == ValueStyle.DOUBLE) {
            return YamlScalars.doubleQuoted(value);
        }
        if (oldStyle == ValueStyle.LITERAL && multiline) {
            return literal(value, keyIndent);
        }
        String style = binding == null ? null : binding.style();
        if ("single".equals(style) && !multiline) {
            return YamlScalars.singleQuoted(value);
        }
        if ("double".equals(style)) {
            return YamlScalars.doubleQuoted(value);
        }
        if ("literal".equals(style) && multiline) {
            return literal(value, keyIndent);
        }
        if ("plain".equals(style) && YamlScalars.isPlainSafe(value, timeTyped)) {
            return value;
        }
        if (YamlScalars.isPlainSafe(value, timeTyped)) {
            return value;
        }
        return "single".equals(binding().text().quote()) && !multiline ? YamlScalars.singleQuoted(value) : YamlScalars.doubleQuoted(value);
    }

    /** A multi-line string written {@code |-}, its lines one step deeper than its key (FBL §6.3). */
    private String literal(String value, int keyIndent) {
        String dominant = text().dominantEnding();
        String newline = dominant != null ? dominant : binding().text().newline();
        String indent = indentation(keyIndent + step());
        StringBuilder builder = new StringBuilder("|-");
        for (String line : value.replace("\r\n", "\n").split("\n", -1)) {
            builder.append(newline);
            if (!line.isEmpty()) {
                builder.append(indent).append(line);
            }
        }
        return builder.toString();
    }

    // ---- writing ----

    @Override
    public void write(Plan plan, ReadElement element, List<SlotChange> changes) {
        for (SlotChange change : changes) {
            SlotRead read = change.read();
            TreeEntry member = read.node() instanceof TreeEntry tree ? tree : null;
            if (change.isEmpty() && "remove".equals(change.binding().empty())) {
                if (member != null) {
                    plan.add(SpliceOperation.REMOVE_KEY, member.lineSpan() != null ? member.lineSpan() : member.own(), "");
                }
                continue;
            }
            if (read.present() && member != null) {
                String written = format(read, change.binding(), change.value());
                if (!written.equals(text().text(member.value().span()))) {
                    plan.add(SpliceOperation.REPLACE_VALUE, member.value().span(), written);
                }
                continue;
            }
            TreeValue mapping = read.node() instanceof TreeValue tree ? tree : mapping((TreeEntry) element.entry(), change.binding().child());
            String key = change.binding().key();
            if (mapping == null || mapping.kind() != ValueKind.MAPPING || key == null) {
                Plan.refuse("The " + binding().name() + " file has no mapping to write '" + (key == null ? "" : key) + "' in.");
                return;
            }
            insertKey(plan, mapping, key, change);
        }
    }

    /** A key the entry lacks goes at its place in the rule's key order: after the nearest present key before it, else before the nearest after it. */
    private void insertKey(Plan plan, TreeValue mapping, String key, SlotChange change) {
        List<String> order = keyOrder(change.rule(), change.binding().child());
        int index = order.indexOf(key);
        int offset;
        TreeEntry before = null;
        for (int i = index - 1; i >= 0 && before == null; i--) {
            before = mapping.member(order.get(i));
        }
        List<TreeEntry> members = mapping.entries();
        if (before != null) {
            offset = lineEndAfter(before);
        } else {
            TreeEntry after = null;
            for (int i = index + 1; i < order.size() && after == null; i++) {
                after = mapping.member(order.get(i));
            }
            offset = after != null && after.lineSpan() != null ? after.lineSpan().start() : lineEndAfter(members.get(members.size() - 1));
        }
        String indent = indentation(members.get(0).indent());
        String line = indent + key + ": " + scalar(change.value(), null, change.binding(), members.get(0).indent());
        plan.add(SpliceOperation.INSERT_KEY, offset, offset, newLine(offset, List.of(line)));
    }

    /**
     * The order keys are written in: the rule's {@code insert.keys}, then its bindings' keys in binding order.
     *
     * @param child the selector of the child the keys are written in, or null for the entry itself
     */
    private static List<String> keyOrder(Rule rule, String child) {
        List<String> order = new ArrayList<>();
        if (child == null && rule.insert() != null) {
            order.addAll(rule.insert().keys());
        }
        addKey(order, rule.id() == null ? null : rule.id().from(), child);
        addKey(order, rule.source(), child);
        addKey(order, rule.target(), child);
        for (AttributeBinding binding : rule.attributes().values()) {
            addKey(order, binding, child);
        }
        return order;
    }

    private static void addKey(List<String> order, Slot slot, String child) {
        if (slot != null && slot.key() != null && Objects.equals(slot.child(), child) && !order.contains(slot.key())) {
            order.add(slot.key());
        }
    }

    /**
     * The text of new lines inserted at {@code offset}, a line start: each line with the ending of
     * the line the insertion point is on. At the end of a body without a final newline the break
     * goes before the text and none after it (FBL §6.3).
     */
    private String newLine(int offset, List<String> lines) {
        String newline = offset > 0 ? newlineAt(offset - 1) : newlineAt(0);
        List<TextLine> bodyLines = text().lines();
        boolean breakFirst = offset == text().length() && text().length() > 0 && bodyLines.get(bodyLines.size() - 1).ending().isEmpty();
        StringBuilder builder = new StringBuilder();
        for (String line : lines) {
            if (breakFirst) {
                builder.append(newline).append(line);
            } else {
                builder.append(line).append(newline);
            }
        }
        return builder.toString();
    }

    /** The columns between a key and the {@code -} of its sequence's items, as the body shows it, else {@code text.sequenceIndent}. */
    private int sequenceOffset() {
        for (Entry entry : allEntries) {
            if (entry instanceof TreeEntry tree && tree.keySpan() != null && tree.lineSpan() != null && tree.value().kind() == ValueKind.SEQUENCE
                    && tree.value().entries().size() > 0) {
                return tree.value().entries().get(0).indent() - tree.indent();
            }
        }
        return binding().text().sequenceFlush() ? 0 : step();
    }

    @Override
    public void insert(Plan plan, InsertRequest request) {
        Rule rule = request.rule();
        InsertSettings insert = rule.insert();
        Entry parent = request.parent() != null ? request.parent().entry() : endParent(request);
        Map<String, String> captures = request.parent() != null ? request.parent().candidate().captures() : null;
        if (captures == null && parent != null) {
            ReadElement enclosing = plan.reading().elementOf(parent);
            captures = enclosing == null ? null : enclosing.candidate().captures();
        }
        if (captures == null) {
            captures = capturesOf(plan);
        }
        String selector = insert.container() != null ? insert.container() : containerOf(rule.at());
        TreeEntry container = container(selector, parent, captures);
        List<String> keys = itemLines(request);
        if (keys.isEmpty()) {
            Plan.refuse("A new " + nothingForNull(rule.type()) + " would have no keys to write.");
            return;
        }
        if (container == null) {
            ensureContainer(plan, insert, selector, parent, keys);
            return;
        }
        TreeValue value = container.value();
        if (value.kind() == ValueKind.SEQUENCE) {
            List<TreeEntry> siblings = value.entries();
            TreeEntry previous = siblings.isEmpty() ? null : siblings.get(siblings.size() - 1);
            if ("start".equals(insert.place())) {
                previous = null;
            } else if ("after-last".equals(insert.place())) {
                for (int i = siblings.size() - 1; i >= 0; i--) {
                    if (Objects.equals(plan.reading().claimedBy(siblings.get(i)), rule.name())) {
                        previous = siblings.get(i);
                        break;
                    }
                }
            }
            TreeEntry model = previous != null ? previous : siblings.get(0);
            int dash = model.indent();
            int keyIndent = model.value().kind() == ValueKind.MAPPING && model.value().entries().size() > 0 ? model.value().entries().get(0).indent() : dash + 2;
            int offset;
            if (previous == null) {
                TreeEntry first = siblings.get(0);
                offset = first.lineSpan() != null ? first.lineSpan().start() : first.own().start();
            } else {
                offset = lineEndAfter(previous);
            }
            plan.add(SpliceOperation.INSERT_ENTRY, offset, offset, newLine(offset, item(keys, dash, keyIndent)));
            return;
        }
        if (value.style() == ValueStyle.EMPTY) {
            int offset = lineEndAfter(container);
            int dash = container.indent() + sequenceOffset();
            plan.add(SpliceOperation.INSERT_ENTRY, offset, offset, newLine(offset, item(keys, dash, dash + 2)));
            return;
        }
        Span keySpan = container.keySpan();
        if (value.style() == ValueStyle.FLOW_SEQUENCE && value.flow() instanceof List<?> members && members.isEmpty() && keySpan != null) {
            // An empty flow sequence ("elements: []", the template's) becomes a block sequence: the
            // flow value goes and the item follows on its own line.
            int colon = text().text(keySpan.end(), value.span().start()).indexOf(':');
            plan.add(SpliceOperation.REPLACE_VALUE, keySpan.end() + colon + 1, value.span().end(), "");
            int offset = lineEndAfter(container);
            int dash = container.indent() + sequenceOffset();
            plan.add(SpliceOperation.INSERT_ENTRY, offset, offset, newLine(offset, item(keys, dash, dash + 2)));
            return;
        }
        Plan.refuse("The " + selector + " of this file is not a list a " + nothingForNull(rule.type()) + " can be added to.");
    }

    /** @param at a rule's selector, or null */
    private static String containerOf(String at) {
        if (at == null) {
            return "/";
        }
        int cut = at.lastIndexOf('/');
        return cut <= 0 ? "/" : at.substring(0, cut);
    }

    /** The entry a relation is written inside when one of its ends is its enclosing entry (a dependency written inside the entry that depends). Null when neither is. */
    private static Entry endParent(InsertRequest request) {
        if (request.rule().target() != null && request.rule().target().parent() != null) {
            return request.target() == null ? null : request.target().entry();
        }
        if (request.rule().source() != null && request.rule().source().parent() != null) {
            return request.source() == null ? null : request.source().entry();
        }
        return null;
    }

    private void ensureContainer(Plan plan, InsertSettings insert, String selector, Entry parent, List<String> keys) {
        CreateContainer create = insert.create();
        if (create == null) {
            Plan.refuse("The file has no " + trim(selector, true, false) + " to add to.");
            return;
        }
        String trimmed = trim(selector, false, true);
        String name = trimmed.substring(trimmed.lastIndexOf('/') + 1);
        TreeEntry owner = selector.startsWith("/") ? root() : parent instanceof TreeEntry tree ? tree : null;
        if (selector.startsWith("/") && trim(selector, true, true).contains("/")) {
            owner = container(containerOf(selector), parent, Map.of());
        }
        TreeValue mapping = owner == null ? null : owner.value();
        String at = create.at() == null ? "" : create.at();
        String argument = create.argument();
        TreeEntry named = mapping == null || argument == null ? null : mapping.member(argument);
        int offset;
        int indent;
        if (at.equals("end-of-document")) {
            offset = text().length();
            indent = mapping != null && mapping.kind() == ValueKind.MAPPING && mapping.entries().size() > 0 ? mapping.entries().get(0).indent() : 0;
        } else if (at.equals("after") && named != null) {
            offset = lineEndAfter(named);
            indent = named.indent();
        } else if (at.equals("before") && named != null) {
            offset = named.lineSpan() != null ? named.lineSpan().start() : named.own().start();
            indent = named.indent();
        } else {
            TreeEntry holder = at.equals("under") && argument != null ? container(argument, parent, Map.of()) : null;
            if (holder == null || holder.value().kind() != ValueKind.MAPPING || holder.value().entries().isEmpty()) {
                Plan.refuse("The file has no place to create " + name + " in.");
                return;
            }
            List<TreeEntry> under = holder.value().entries();
            offset = lineEndAfter(under.get(under.size() - 1));
            indent = under.get(0).indent();
        }
        String containerText = create.text() != null ? create.text() : indentation(indent) + name + ":";
        plan.add(SpliceOperation.ENSURE_CONTAINER, offset, offset, newLine(offset, List.of(containerText)));
        int dash = indent + sequenceOffset();
        plan.add(SpliceOperation.INSERT_ENTRY, offset, offset, newLine(offset, item(keys, dash, dash + 2)));
    }

    private List<String> item(List<String> lines, int dash, int keyIndent) {
        List<String> item = new ArrayList<>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            item.add(i == 0 ? indentation(dash) + "-" + " ".repeat(Math.max(1, keyIndent - dash - 1)) + lines.get(i) : indentation(keyIndent) + lines.get(i));
        }
        return item;
    }

    /** The key lines of a new item in {@code insert.keys} order, then the skeleton's lines. */
    private List<String> itemLines(InsertRequest request) {
        Rule rule = request.rule();
        InsertSettings insert = rule.insert();
        List<String> lines = new ArrayList<>();
        for (String key : keyOrder(rule, null)) {
            if (insert.keys().size() > 0 && !insert.keys().contains(key)) {
                continue;
            }
            String value = wireValue(request, key);
            if (value != null) {
                lines.add(key + ": " + value);
            }
        }
        String skeleton = insert.skeleton();
        if (skeleton != null) {
            String rendered = NewText.render(skeleton, name -> {
                Object v = request.values().get(name);
                return v != null ? NewText.plain(v, rule.attribute(name)) : name.equals("id") ? request.id() : null;
            });
            lines.addAll(List.of(trim(rendered.replace("\r\n", "\n"), '\n', false, true).split("\n", -1)));
        }
        return lines;
    }

    /** The value a new item's key is written with, or null when the request holds none for it. */
    private String wireValue(InsertRequest request, String key) {
        Rule rule = request.rule();
        if (rule.id() != null && rule.id().from() != null && key.equals(rule.id().from().key()) && request.id() != null) {
            return scalar(request.id(), null, null, 0);
        }
        if (rule.source() != null && key.equals(rule.source().key()) && request.source() != null) {
            return scalar(request.source().key(), null, null, 0);
        }
        if (rule.target() != null && key.equals(rule.target().key()) && request.target() != null) {
            return scalar(request.target().key(), null, null, 0);
        }
        for (Map.Entry<String, AttributeBinding> attribute : rule.attributes().entrySet()) {
            AttributeBinding binding = attribute.getValue();
            if (!key.equals(binding.key()) || binding.child() != null || binding.isComputed()) {
                continue;
            }
            Object value = request.values().get(attribute.getKey());
            if (NewText.isEmpty(value)) {
                continue;
            }
            String wire = NewText.wire(binding, value, null);
            return scalar(wire != null ? wire : value, null, binding, 0);
        }
        return null;
    }

    @Override
    public void remove(Plan plan, ReadElement element, Set<ReadElement> removed) {
        TreeEntry entry = (TreeEntry) element.entry();
        if (entry.isRoot()) {
            Plan.refuse("The whole " + binding().name() + " file cannot be removed.");
            return;
        }
        Span span = entry.lineSpan() != null ? entry.lineSpan() : entry.own();
        if (element.rule().remove() != null && element.rule().remove().removeContainerWhenEmpty() && entry.parent() instanceof TreeEntry container && !container.isRoot()
                && container.lineSpan() != null) {
            Set<Entry> gone = new HashSet<>();
            for (ReadElement r : removed) {
                gone.add(r.entry());
            }
            if (gone.containsAll(container.value().entries())) {
                TreeEntry first = container.value().entries().get(0);
                int firstStart = (first.lineSpan() != null ? first.lineSpan() : first.own()).start();
                Span head = new Span(container.lineSpan().start(), firstStart);
                if (!plan.touches(head)) {
                    plan.add(SpliceOperation.REMOVE_CONTAINER, head, "");
                }
            }
        }
        plan.add(SpliceOperation.REMOVE_ENTRY, span, "");
    }

    private static String nothingForNull(String value) {
        return value == null ? "" : value;
    }

    /** The selector without the slashes at its start, at its end, or both. */
    private static String trim(String selector, boolean start, boolean end) {
        return trim(selector, '/', start, end);
    }

    private static String trim(String value, char c, boolean start, boolean end) {
        int from = 0;
        int to = value.length();
        while (start && from < to && value.charAt(from) == c) {
            from++;
        }
        while (end && to > from && value.charAt(to - 1) == c) {
            to--;
        }
        return value.substring(from, to);
    }
}
