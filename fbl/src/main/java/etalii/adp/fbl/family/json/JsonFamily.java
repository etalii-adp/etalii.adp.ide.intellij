package etalii.adp.fbl.family.json;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.FindingCodes;
import etalii.adp.fbl.FindingSeverity;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.document.AttributeBinding;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.InsertSettings;
import etalii.adp.fbl.document.Rule;
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
 * The {@code json} family (FBL §4.4): an RFC 8259 text read byte for byte, members and items with
 * their own spans and the separator after each, comments refused, duplicate names reported.
 */
public final class JsonFamily extends TreeFamily {

    private final List<Span> leaves = new ArrayList<>();

    public JsonFamily(BodyText text, FblBinding binding, FblOptions options) {
        super(text, binding, options);
    }

    @Override
    public String familyName() {
        return "json";
    }

    @Override
    public List<Span> leaves() {
        return Collections.unmodifiableList(leaves);
    }

    @Override
    public boolean isTrivia(Span gap) {
        byte[] bytes = text().bytes();
        for (int i = gap.start(); i < gap.end(); i++) {
            byte b = bytes[i];
            if (!(b == ' ' || b == '\t' || b == '\r' || b == '\n' || b == '{' || b == '}' || b == '[' || b == ']' || b == ',' || b == ':')) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void parse() {
        JsonValue parsed;
        try {
            parsed = JsonParser.parse(text().bytes(), text().bomLength());
        } catch (JsonSyntaxException error) {
            setUnreadable(new Unreadable(error.offset(), error.getMessage()));
            return;
        }
        TreeValue value = value(parsed);
        TreeEntry root = TreeEntry.root(value.span(), column(value.span().start()), value);
        setRoot(root);
        index(root);
        for (Entry entry : allEntries) {
            if (entry instanceof TreeEntry tree && !tree.isRoot()) {
                tree.setLineSpan(lineSpanOf(tree));
            }
        }
    }

    private int column(int offset) {
        int index = text().lineIndexAt(offset);
        TextLine line = text().lines().get(index);
        return offset - Math.max(line.start(), index == 0 ? text().bomLength() : 0);
    }

    /**
     * The value as the tree keeps it, its strings and literals added to the leaves in the order
     * they are written. A repeated member name is reported once its value is read, and left out.
     */
    private TreeValue value(JsonValue parsed) {
        switch (parsed.kind()) {
            case OBJECT -> {
                TreeValue value = new TreeValue(ValueKind.MAPPING, ValueStyle.BLOCK, parsed.span());
                for (JsonMember each : parsed.members()) {
                    Span keySpan = each.keySpan();
                    leaves.add(keySpan);
                    TreeValue inner = value(each.value());
                    TreeEntry member = TreeEntry.member(each.name(), keySpan, new Span(keySpan.start(), inner.span().end()), column(keySpan.start()), inner);
                    if (!each.duplicate()) {
                        value.entries().add(member);
                    } else {
                        report(FindingCodes.DUPLICATE_KEY, FindingSeverity.WARNING,
                                "The member \"" + each.name() + "\" appears again in this object; only the first is read.", keySpan);
                    }
                    member.setSeparator(each.separator());
                }
                return value;
            }
            case ARRAY -> {
                TreeValue value = new TreeValue(ValueKind.SEQUENCE, ValueStyle.BLOCK, parsed.span());
                for (JsonMember each : parsed.members()) {
                    TreeValue inner = value(each.value());
                    TreeEntry item = TreeEntry.item(inner.span(), column(inner.span().start()), inner);
                    value.entries().add(item);
                    item.setSeparator(each.separator());
                }
                return value;
            }
            case STRING -> {
                leaves.add(parsed.span());
                return new TreeValue(ValueKind.SCALAR, ValueStyle.JSON_STRING, parsed.span(), parsed.text(), parsed.text());
            }
            default -> {
                leaves.add(parsed.span());
                return new TreeValue(ValueKind.SCALAR, ValueStyle.JSON_OTHER, parsed.span(), parsed.text(), parsed.typed());
            }
        }
    }

    /**
     * An entry starts its line when only whitespace precedes it, and ends it when only its
     * separator and whitespace follow; its line span then takes the separator with it.
     * Null when the entry shares a line.
     */
    private Span lineSpanOf(TreeEntry entry) {
        if (!onlyWhitespaceBefore(entry.own().start())) {
            return null;
        }
        byte[] bytes = text().bytes();
        int i = entry.own().end();
        TextLine line = text().lines().get(text().lineIndexAt(Math.max(entry.own().start(), i - 1)));
        while (i < line.contentEnd() && (bytes[i] == ' ' || bytes[i] == '\t')) {
            i++;
        }
        if (i < line.contentEnd() && bytes[i] == ',') {
            i++;
        }
        while (i < line.contentEnd() && (bytes[i] == ' ' || bytes[i] == '\t')) {
            i++;
        }
        if (i != line.contentEnd()) {
            return null;
        }
        return new Span(text().lines().get(text().lineIndexAt(entry.own().start())).start(), line.end());
    }

    // ---- writing ----

    @Override
    public String format(SlotRead read, AttributeBinding binding, Object value) {
        String wire = binding == null ? null : NewText.wire(binding, value, read.wire());
        return wire != null ? quote(wire) : write(value, binding);
    }

    /**
     * A model value as JSON text: a string quoted, a number by the rule of FBL §6.3, a list in one line.
     *
     * @param value the model value, or null
     * @param binding the attribute's binding, or null
     */
    public static String write(Object value, AttributeBinding binding) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String s) {
            return quote(s);
        }
        if (value instanceof Boolean b) {
            return b ? "true" : "false";
        }
        Double n = NewText.tryNumber(value);
        if (n != null) {
            return NewText.number(n, binding == null ? null : binding.decimals());
        }
        if (value instanceof Iterable<?> list) {
            StringBuilder builder = new StringBuilder("[");
            boolean first = true;
            for (Object v : list) {
                if (!first) {
                    builder.append(", ");
                }
                first = false;
                builder.append(write(v, binding));
            }
            return builder.append(']').toString();
        }
        return quote(String.valueOf(value));
    }

    /** A JSON string with the escapes RFC 8785 uses (FBL §6.3). */
    public static String quote(String value) {
        return "\"" + escape(value) + "\"";
    }

    /** The text between the quotes of a JSON string, with the escapes RFC 8785 uses (FBL §6.3). */
    public static String escape(String value) {
        StringBuilder builder = new StringBuilder(value.length() + 2);
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\b' -> builder.append("\\b");
                case '\f' -> builder.append("\\f");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        String hex = Integer.toHexString(c);
                        builder.append("\\u").repeat('0', 4 - hex.length()).append(hex);
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    @Override
    public void write(Plan plan, ReadElement element, List<SlotChange> changes) {
        for (SlotChange change : changes) {
            SlotRead read = change.read();
            if (change.isEmpty() && "remove".equals(change.binding().empty())) {
                if (read.node() instanceof TreeEntry member) {
                    plan.add(SpliceOperation.REMOVE_KEY, removalSpan(member), "");
                }
                continue;
            }
            if (read.present() && read.span() != null) {
                Span span = read.span();
                String written = format(read, change.binding(), change.value());
                if (!written.equals(text().text(span))) {
                    plan.add(SpliceOperation.REPLACE_VALUE, span, written);
                }
                continue;
            }
            TreeValue mapping = mapping((TreeEntry) element.entry(), change.binding().child());
            String key = change.binding().key();
            if (mapping == null || key == null) {
                Plan.refuse("The " + binding().name() + " file has no object to write \"" + (key == null ? "" : key) + "\" in.");
                return;
            }
            NewMember added = newMember(mapping, quote(key) + ": " + format(read, change.binding(), change.value()));
            plan.add(SpliceOperation.INSERT_KEY, added.offset(), added.offset(), added.text());
        }
    }

    private record NewMember(int offset, String text) {
    }

    /** A new member or item after a container's last entry, with the previous entry's separator (FBL §6.3). */
    private NewMember newMember(TreeValue container, String text) {
        if (container.entries().isEmpty()) {
            return new NewMember(container.span().start() + 1, text);
        }
        TreeEntry last = container.entries().get(container.entries().size() - 1);
        if (last.lineSpan() != null) {
            return new NewMember(last.own().end(), "," + newlineAt(last.own().end()) + indentation(last.indent()) + text);
        }
        return new NewMember(last.own().end(), ", " + text);
    }

    @Override
    public void insert(Plan plan, InsertRequest request) {
        InsertSettings insert = request.rule().insert();
        TreeEntry container = insert.container() == null ? null
                : container(insert.container(), request.parent() == null ? null : request.parent().entry(), capturesOf(plan));
        if (container == null || container.value().kind() == ValueKind.SCALAR) {
            Plan.refuse("The " + binding().name() + " file has no " + (insert.container() != null ? insert.container() : "container") + " to add the "
                    + request.rule().type() + " to.");
            return;
        }
        String text;
        if (insert.emit() != null) {
            text = NewText.render(insert.emit(), name -> switch (name) {
                case "id" -> request.id() == null ? null : escape(request.id());
                case "source" -> request.source() == null ? null : escape(request.source().key());
                case "target" -> request.target() == null ? null : escape(request.target().key());
                default -> {
                    Object v = request.values().get(name);
                    yield v != null ? escape(NewText.plain(v, request.rule().attribute(name))) : null;
                }
            });
        } else {
            List<String> members = new ArrayList<>();
            for (String key : insert.keys()) {
                String value = wireValue(request, key);
                if (value != null) {
                    members.add(quote(key) + ": " + value);
                }
            }
            text = "{ " + String.join(", ", members) + " }";
        }
        if ("start".equals(insert.place()) && !container.value().entries().isEmpty()) {
            TreeEntry first = container.value().entries().get(0);
            boolean newline = first.lineSpan() != null;
            plan.add(SpliceOperation.INSERT_ENTRY, first.own().start(), first.own().start(),
                    text + (newline ? "," + newlineAt(first.own().start()) + indentation(first.indent()) : ", "));
            return;
        }
        NewMember added = newMember(container.value(), text);
        plan.add(SpliceOperation.INSERT_ENTRY, added.offset(), added.offset(), added.text());
    }

    /** The JSON text of the value a new entry's {@code key} gets, or null when the request has none for it. */
    private static String wireValue(InsertRequest request, String key) {
        Rule rule = request.rule();
        if (rule.id() != null && rule.id().from() != null && key.equals(rule.id().from().key()) && request.id() != null) {
            return quote(request.id());
        }
        if (rule.source() != null && key.equals(rule.source().key()) && request.source() != null) {
            return quote(request.source().key());
        }
        if (rule.target() != null && key.equals(rule.target().key()) && request.target() != null) {
            return quote(request.target().key());
        }
        for (Map.Entry<String, AttributeBinding> each : rule.attributes().entrySet()) {
            AttributeBinding binding = each.getValue();
            if (key.equals(binding.key()) && binding.child() == null) {
                Object value = request.values().get(each.getKey());
                if (value != null) {
                    return write(value, binding);
                }
            }
        }
        return null;
    }

    /**
     * What removing an entry takes (FBL §6.2): its line span when it has one and is not the last of
     * several, else its own span with one separator: the one after it, or, for the last entry, the
     * one before it and the whitespace in between.
     */
    private Span removalSpan(TreeEntry entry) {
        List<TreeEntry> siblings = ((TreeEntry) entry.parent()).value().entries();
        int index = siblings.indexOf(entry);
        boolean isLast = index == siblings.size() - 1;
        if (isLast && index > 0) {
            TreeEntry previous = siblings.get(index - 1);
            return new Span(previous.separator(), entry.own().end());
        }
        if (entry.lineSpan() != null) {
            return entry.lineSpan();
        }
        if (!isLast) {
            return new Span(entry.own().start(), siblings.get(index + 1).own().start());
        }
        return entry.own();
    }

    @Override
    public void remove(Plan plan, ReadElement element, Set<ReadElement> removed) {
        TreeEntry entry = (TreeEntry) element.entry();
        if (entry.isRoot()) {
            Plan.refuse("The whole " + binding().name() + " file cannot be removed.");
            return;
        }
        plan.add(SpliceOperation.REMOVE_ENTRY, removalSpan(entry), "");
    }
}
