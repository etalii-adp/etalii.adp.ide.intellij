package etalii.adp.fbl.rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import etalii.adp.fbl.FblElement;
import etalii.adp.fbl.FblModel;
import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.FblView;
import etalii.adp.fbl.Finding;
import etalii.adp.fbl.FindingCodes;
import etalii.adp.fbl.FindingSeverity;
import etalii.adp.fbl.IdRequest;
import etalii.adp.fbl.SourceLocation;
import etalii.adp.fbl.document.AttributeBinding;
import etalii.adp.fbl.document.BindingReader;
import etalii.adp.fbl.document.BlockRule;
import etalii.adp.fbl.document.Family;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.HeaderSettings;
import etalii.adp.fbl.document.ParentBinding;
import etalii.adp.fbl.document.ReferenceBinding;
import etalii.adp.fbl.document.Rule;
import etalii.adp.fbl.document.Slot;
import etalii.adp.fbl.expression.CelCompiler;
import etalii.adp.fbl.expression.CelContext;
import etalii.adp.fbl.expression.CelError;
import etalii.adp.fbl.expression.CelException;
import etalii.adp.fbl.expression.CelMap;
import etalii.adp.fbl.expression.CelProgram;
import etalii.adp.fbl.family.json.JsonFamily;
import etalii.adp.fbl.family.json.JsonValue;
import etalii.adp.fbl.family.lines.LinesFamily;
import etalii.adp.fbl.family.xml.XmlFamily;
import etalii.adp.fbl.family.yaml.YamlFamily;
import etalii.adp.fbl.plan.NewText;
import etalii.adp.fbl.text.BodyText;
import etalii.adp.fbl.text.Span;

/**
 * A body read through a binding (FBL §5): the family's lossless reading, which rule claimed which
 * entry, the elements and relations with where each value lives, and the findings. Reading is a
 * pure function of the bytes, the binding and the options, and never throws on content (FBL §7.4).
 */
public final class BodyReading {

    private final Map<Entry, String> claims = new HashMap<>();
    private final Map<Entry, ReadElement> byEntry = new HashMap<>();
    private final Map<String, CelProgram> programs = new HashMap<>();
    private final List<Finding> findings = new ArrayList<>();
    private final BodyText text;
    private final FblBinding binding;
    private final FblOptions options;
    private final FamilyReader family;
    private final List<ReadElement> elements = new ArrayList<>();
    private final List<FblView> views = new ArrayList<>();
    private final List<String> resources = new ArrayList<>();
    private Unreadable unreadable;

    /**
     * The registration's {@code resource} header selects one value of the binding's resource capture;
     * without it, the first value in document order (FBL §8.2).
     */
    private String resource;

    private BodyReading(BodyText text, FblBinding binding, FblOptions options, FamilyReader family) {
        this.text = text;
        this.binding = binding;
        this.options = options;
        this.family = family;
    }

    /**
     * What evaluating a CEL expression gave.
     *
     * @param value the value, or null: also null when the evaluation failed
     * @param problem why the expression could not be compiled or evaluated, or null
     */
    public record Evaluation(Object value, String problem) {
    }

    /** A compiled expression, or null with the reason it does not compile. */
    private record Compiled(CelProgram program, String problem) {
    }

    public BodyText text() {
        return text;
    }

    public FblBinding binding() {
        return binding;
    }

    public FblOptions options() {
        return options;
    }

    public FamilyReader family() {
        return family;
    }

    /** Elements and relations in document order; relations whose ends name nothing are not among them. The list is the reading's own. */
    public List<ReadElement> elements() {
        return elements;
    }

    public List<Finding> findings() {
        return Collections.unmodifiableList(findings);
    }

    /** The views the body's view blocks define (FBL §4.7), in document order. The list is the reading's own. */
    public List<FblView> views() {
        return views;
    }

    /** The values of the binding's resource capture (FBL §8.2) in document order: the resources the body holds. The list is the reading's own. */
    public List<String> resources() {
        return resources;
    }

    /** Where and why the body is unreadable (FBL §7.5), or null when it is read. */
    public Unreadable unreadable() {
        return unreadable;
    }

    /** The name of the rule or block that claimed {@code entry}, or null. */
    public String claimedBy(Entry entry) {
        return claims.get(entry);
    }

    /** The element or relation read from {@code entry}, or null. */
    public ReadElement elementOf(Entry entry) {
        return byEntry.get(entry);
    }

    /** The element or relation of that id, or null. */
    public ReadElement find(String id) {
        for (ReadElement element : elements) {
            if (element.id().equals(id)) {
                return element;
            }
        }
        return null;
    }

    public static FamilyReader createFamily(BodyText text, FblBinding binding, FblOptions options) {
        Family family = binding.body().family();
        if (family == null) {
            throw new IllegalStateException("The binding '" + binding.name() + "' declares no family.");
        }
        String extension = extension(options.fileName()).toLowerCase(Locale.ROOT);
        for (Family also : binding.body().alsoRead()) {
            if (familyOfExtension(extension) == also) {
                family = also;
            }
        }
        return switch (family) {
            case LINES -> new LinesFamily(text, binding, options, false);
            case BLOCKS -> new LinesFamily(text, binding, options, true);
            case YAML -> new YamlFamily(text, binding, options);
            case JSON -> new JsonFamily(text, binding, options);
            case XML -> new XmlFamily(text, binding, options);
        };
    }

    /** The extension of a file name, its period included: empty when the name has none or ends in a period. */
    private static String extension(String fileName) {
        for (int i = fileName.length() - 1; i >= 0; i--) {
            char c = fileName.charAt(i);
            if (c == '.') {
                return i == fileName.length() - 1 ? "" : fileName.substring(i);
            }
            if (c == '/' || c == '\\') {
                break;
            }
        }
        return "";
    }

    private static Family familyOfExtension(String extension) {
        return switch (extension) {
            case ".yml", ".yaml" -> Family.YAML;
            case ".json" -> Family.JSON;
            case ".xml" -> Family.XML;
            default -> null;
        };
    }

    public static BodyReading read(byte[] bytes, FblBinding binding, FblOptions options) {
        BodyText text = new BodyText(bytes);
        FamilyReader family = createFamily(text, binding, options);
        BodyReading reading = new BodyReading(text, binding, options, family);
        if (bytes.length > options.maxBodyBytes()) {
            return reading.makeUnreadable(0, "The body is larger than the " + options.maxBodyBytes() + " bytes this host reads, so it is not read at all.");
        }
        if (!text.isValidUtf8()) {
            return reading.makeUnreadable(text.invalidOffset(), "The body is not valid UTF-8.");
        }
        family.parse();
        Unreadable problem = family.unreadable();
        if (problem != null) {
            return reading.makeUnreadable(problem.offset(), problem.message());
        }
        if (family.entries().size() > options.maxEntries()) {
            return reading.makeUnreadable(0, "The body has more than the " + options.maxEntries() + " entries this host reads, so it is not read at all.");
        }
        HeaderSettings header = binding.header();
        if (header != null && !family.headerHolds(header)) {
            String mark = header.key() != null
                    ? "'" + header.key() + ": " + (header.value() != null ? BindingReader.scalarText(header.value()) : "") + "'"
                    : "its first line";
            if (header.required()) {
                return reading.makeUnreadable(0, "The body does not start with the mark the binding requires (" + mark + ").");
            }
            family.report(FindingCodes.HEADER_MISMATCH, FindingSeverity.WARNING,
                    "The body does not carry the binding's header mark (" + mark + "); it is read anyway.",
                    new Span(text.bomLength(), text.lines().get(0).contentEnd()));
        }
        reading.claim();
        reading.resolve();
        family.afterRead(reading::claimedBy);
        reading.findings.addAll(0, family.findings());
        return reading;
    }

    private BodyReading makeUnreadable(int offset, String message) {
        unreadable = new Unreadable(offset, message);
        claims.clear();
        byEntry.clear();
        elements.clear();
        findings.clear();
        BodyText.Position position = text.position(Math.min(offset, text.length()));
        findings.add(new Finding(FindingCodes.UNPARSEABLE, FindingSeverity.ERROR, message,
                new SourceLocation(options.fileName(), position.line(), position.column(), 0)));
        return this;
    }

    // ---- claiming entries (FBL §5.1) ----

    private void claim() {
        Map<Entry, List<Candidate>> offered = new HashMap<>();
        for (BlockRule block : binding.blocks()) {
            for (Candidate candidate : family.blockCandidates(block)) {
                offer(offered, candidate);
            }
        }
        for (Rule rule : binding.allRules()) {
            for (Candidate candidate : family.candidates(rule)) {
                offer(offered, candidate);
            }
        }
        for (Entry entry : family.entries()) {
            List<Candidate> candidates = offered.get(entry);
            if (candidates == null) {
                continue;
            }
            String error = null;
            for (Candidate candidate : candidates) {
                List<String> within = candidate.rule() != null ? candidate.rule().within() : null;
                if (within == null && candidate.block() != null) {
                    within = candidate.block().within();
                }
                if (!family.admits(within, entry, this::claimedBy)) {
                    continue;
                }
                Rule rule = candidate.rule();
                if (rule != null) {
                    if (!selectedResource(candidate)) {
                        continue;
                    }
                    if (rule.when() != null) {
                        Evaluation holds = evaluate(rule.when(), candidate);
                        if (holds.problem() != null && error == null) {
                            error = holds.problem();
                        }
                        if (!Boolean.TRUE.equals(holds.value())) {
                            continue;
                        }
                    }
                    claims.put(entry, rule.name());
                    ReadElement element = new ReadElement(rule, candidate);
                    element.setLine(text.position(entry.own().start()).line());
                    byEntry.put(entry, element);
                    elements.add(element);
                    readSlots(element);
                } else {
                    BlockRule block = candidate.block();
                    claims.put(entry, block.name());
                    String view = block.view() != null ? candidate.captures().get(block.view()) : null;
                    if (view != null) {
                        views.add(new FblView(view, block.name(), entry.own(), text.position(entry.own().start()).line()));
                    }
                }
                error = null;
                break;
            }
            if (error != null) {
                family.report(FindingCodes.UNREADABLE_ENTRY, FindingSeverity.WARNING, "This entry cannot be read: " + error, entry.own());
            }
        }
    }

    private static void offer(Map<Entry, List<Candidate>> offered, Candidate candidate) {
        List<Candidate> list = offered.get(candidate.entry());
        if (list == null) {
            list = new ArrayList<>();
            offered.put(candidate.entry(), list);
        }
        list.add(candidate);
    }

    private boolean selectedResource(Candidate candidate) {
        String capture = binding.registration().resourceCapture();
        if (capture == null) {
            return true;
        }
        String value = candidate.captures().get(capture);
        if (value == null) {
            return true;
        }
        if (!resources.contains(value)) {
            resources.add(value);
        }
        if (resource == null) {
            resource = options.resource() != null ? options.resource() : value;
        }
        return value.equals(resource);
    }

    // ---- CEL ----

    private Compiled program(String expression, CelContext context) {
        String key = context + ":" + expression;
        if (programs.containsKey(key)) {
            // An expression that did not compile reports why once, where it was first met.
            return new Compiled(programs.get(key), null);
        }
        CelProgram cached;
        String problem = null;
        try {
            cached = CelCompiler.compile(expression, context);
        } catch (CelException e) {
            problem = e.getMessage();
            cached = null;
        }
        programs.put(key, cached);
        return new Compiled(cached, problem);
    }

    private CelContext ruleContext() {
        return family instanceof LinesFamily ? CelContext.LINES : CelContext.TREE;
    }

    /** Evaluates a rule's expression for {@code candidate}: the value, or the problem that kept it from being computed. */
    public Evaluation evaluate(String expression, Candidate candidate) {
        Compiled compiled = program(expression, ruleContext());
        if (compiled.program() == null) {
            return new Evaluation(null, compiled.problem());
        }
        try {
            Object value = compiled.program().evaluate(variables(candidate));
            if (!(value instanceof CelError failed)) {
                return new Evaluation(value, compiled.problem());
            }
            return new Evaluation(null, failed.message());
        } catch (CelException e) {
            return new Evaluation(null, e.getMessage());
        }
    }

    /**
     * Whether an {@code insert.when} expression holds for the attributes of a new element.
     *
     * @param attributes the values by attribute name; a value may be null
     */
    public boolean insertAllowed(String expression, Map<String, Object> attributes) {
        Compiled compiled = program(expression, CelContext.INSERT);
        if (compiled.program() == null) {
            return false;
        }
        CelMap map = new CelMap();
        for (Map.Entry<String, Object> attribute : attributes.entrySet()) {
            Object value = attribute.getValue();
            map.put(attribute.getKey(), value instanceof Integer i ? Long.valueOf(i) : value);
        }
        try {
            Map<String, Object> variables = new HashMap<>();
            variables.put("attributes", map);
            return compiled.program().isTrue(variables);
        } catch (CelException e) {
            return false;
        }
    }

    private Map<String, Object> variables(Candidate candidate) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("entry", family.celValue(candidate));
        variables.put("line", (long) text.position(candidate.entry().own().start()).line());
        CelMap registration = new CelMap();
        registration.putAll(options.registrationHeaders());
        variables.put("registration", registration);
        CelExtra extra = family.celExtra(candidate);
        variables.put(extra.name(), extra.value());
        Object parent = null;
        // Only the nearest enclosing entry is the parent: as its element when a rule claimed it, else as the bare entry.
        List<Entry> enclosing = family.enclosing(candidate.entry());
        if (!enclosing.isEmpty()) {
            Entry nearest = enclosing.get(0);
            ReadElement element = byEntry.get(nearest);
            parent = element != null ? family.celValue(element.candidate()) : family.celValue(new Candidate(null, null, nearest, Map.of()));
        }
        variables.put("parent", parent);
        return variables;
    }

    // ---- slots (FBL §5.2) ----

    private void readSlots(ReadElement element) {
        Rule rule = element.rule();
        for (Map.Entry<String, AttributeBinding> attribute : rule.attributes().entrySet()) {
            String name = attribute.getKey();
            AttributeBinding binding = attribute.getValue();
            SlotRead read = readSlot(element.candidate(), binding, element);
            element.slots().put(name, read);
            Object value = read.present() ? read.value() : null;
            if (read.present() && binding.map() != null && value instanceof String wire && binding.map().containsKey(wire)) {
                value = binding.map().get(wire);
            }
            ReferenceBinding reference = binding.reference();
            if (binding.flag()) {
                value = read.present();
            } else if (read.present() && reference != null && !reference.to().contains(rule.name()) && read.words() != null && !read.words().isEmpty()) {
                List<Object> words = new ArrayList<>();
                for (Word word : read.words()) {
                    words.add(word.text());
                }
                value = words;
            }
            if (!read.present() && !binding.flag() && binding.defaultValue() != null) {
                value = fromJson(binding.defaultValue());
            }
            if (read.present() || binding.flag() || binding.defaultValue() != null) {
                element.attributes().put(name, value);
            }
        }
        if (rule.source() != null) {
            element.setSourceRead(readSlot(element.candidate(), rule.source(), element));
        }
        if (rule.target() != null) {
            element.setTargetRead(readSlot(element.candidate(), rule.target(), element));
        }
        if (rule.id() != null && rule.id().from() != null) {
            element.setIdRead(readSlot(element.candidate(), rule.id().from(), element));
        }
    }

    /** Reads {@code slot} of the candidate's entry for {@code element}, read-only where the attribute, the rule or the binding says so. */
    public SlotRead readSlot(Candidate candidate, Slot slot, ReadElement element) {
        SlotRead read = readSlotUnchecked(candidate, slot, element);
        String reason = slot instanceof AttributeBinding attribute ? attribute.readOnly() : null;
        if (reason == null) {
            reason = element.rule().readOnly();
        }
        if (reason == null) {
            reason = binding.readOnly();
        }
        if (reason != null && read.writable()) {
            read = read.withWritable(false).withReason(reason);
        }
        return read;
    }

    private SlotRead readSlotUnchecked(Candidate candidate, Slot slot, ReadElement element) {
        String expression = slot.value();
        if (expression != null) {
            Evaluation evaluation = evaluate(expression, candidate);
            return evaluation.problem() == null
                    ? new SlotRead(evaluation.value(), null, true, false, "The value is computed from the file.")
                    : SlotRead.readOnlyAbsent("The value cannot be computed: " + evaluation.problem());
        }
        String name = slot.parent();
        if (name != null) {
            for (Entry enclosing : family.enclosing(candidate.entry())) {
                ReadElement parent = byEntry.get(enclosing);
                if (parent == null) {
                    continue;
                }
                ParentBinding containment = element.rule().parent();
                if (containment != null && !containment.rules().contains(parent.rule().name())) {
                    continue;
                }
                SlotRead read = parent.slots().get(name);
                return read != null ? read : family.readRaw(enclosing, name);
            }
            return SlotRead.readOnlyAbsent("No enclosing entry holds this value.");
        }
        if (slot instanceof AttributeBinding attribute && attribute.override() != null) {
            Slot over = attribute.override();
            SlotRead overriding = family.read(candidate, new AttributeBinding(
                    new Slot(over.key(), over.xmlAttribute(), over.text(), over.child(), over.group(), null, over.capture(), null, over.word(), false),
                    null, Map.of(), null, null, false, null, null, null, null, attribute.htmlParagraphs(), null, null));
            if (overriding.present()) {
                return overriding.withNode(new OverrideNode(overriding.node(), over));
            }
        }
        return family.read(candidate, slot);
    }

    private static Object fromJson(JsonValue value) {
        return switch (value.kind()) {
            case STRING -> value.text();
            case NUMBER -> value.typed();
            case TRUE -> Boolean.TRUE;
            case FALSE -> Boolean.FALSE;
            case NULL, OBJECT, ARRAY -> null;
        };
    }

    // ---- ids, references, containment (FBL §5.3 to §5.5) ----

    private void resolve() {
        for (ReadElement element : elements) {
            if (!element.isRelation()) {
                assignId(element);
            }
        }
        for (ReadElement element : elements) {
            if (!element.isRelation()) {
                element.setKey(keyOf(element));
            }
        }
        List<ReadElement> dangling = new ArrayList<>();
        for (ReadElement relation : elements) {
            if (!relation.isRelation()) {
                continue;
            }
            relation.setSourceElement(end(relation, relation.sourceRead(), "source"));
            relation.setTargetElement(end(relation, relation.targetRead(), "target"));
            if (relation.sourceElement() == null || relation.targetElement() == null) {
                dangling.add(relation);
            }
        }
        for (ReadElement relation : dangling) {
            elements.remove(relation);
            byEntry.remove(relation.entry());
        }
        for (ReadElement relation : elements) {
            if (!relation.isRelation()) {
                continue;
            }
            assignId(relation);
            relation.setKey(keyOf(relation));
        }
        Set<String> seen = new HashSet<>();
        for (ReadElement element : elements) {
            if (seen.add(element.id())) {
                continue;
            }
            family.report(FindingCodes.DUPLICATE_ID, FindingSeverity.WARNING,
                    "Another entry already has the id '" + element.id() + "'; this one is addressed by its place.", element.entry().own());
            element.setId(placeId(element));
            element.setIdStored(false);
            seen.add(element.id());
        }
        for (ReadElement element : elements) {
            ParentBinding containment = element.rule().parent();
            if (containment == null) {
                continue;
            }
            for (Entry enclosing : family.enclosing(element.entry())) {
                ReadElement parent = byEntry.get(enclosing);
                if (parent != null && containment.rules().contains(parent.rule().name())) {
                    element.setParent(parent);
                    break;
                }
            }
        }
        for (ReadElement element : elements) {
            checkReferences(element);
        }
    }

    private void assignId(ReadElement element) {
        Rule rule = element.rule();
        if (rule.id() != null && rule.id().from() != null) {
            SlotRead read = element.idRead();
            if (read != null && read.present() && read.value() != null) {
                String id = NewText.plain(read.value(), null);
                if (id != null && !id.isEmpty()) {
                    element.setId(id);
                    element.setIdStored(true);
                    return;
                }
            }
            family.report(FindingCodes.MISSING_ID, FindingSeverity.WARNING, "This " + rule.type() + " has no id; it is addressed by its place.",
                    element.entry().own());
            element.setId(placeId(element));
            return;
        }
        if (rule.id() != null && rule.id().sidecarKey() != null) {
            Object key = evaluate(rule.id().sidecarKey(), element.candidate()).value();
            if (key != null) {
                String stored = options.identities().get(NewText.plain(key, null));
                if (stored != null) {
                    element.setId(stored);
                    element.setIdStored(true);
                    return;
                }
            }
        }
        IdRequest request = new IdRequest(rule.name(), rule.type(), element.attributes(),
                element.sourceElement() != null ? element.sourceElement().id() : null,
                element.targetElement() != null ? element.targetElement().id() : null,
                element.line());
        String derived = options.deriveId() != null ? options.deriveId().apply(request) : null;
        element.setId(derived != null ? derived : placeId(element));
    }

    private static String placeId(ReadElement element) {
        return element.rule().name() + "@" + element.line();
    }

    /**
     * The value references name an element by: the attribute its own rules reference it by, else
     * its stored id, else its id (FBL §5.7).
     */
    private static String keyOf(ReadElement element) {
        for (Map.Entry<String, AttributeBinding> attribute : element.rule().attributes().entrySet()) {
            String name = attribute.getKey();
            ReferenceBinding reference = attribute.getValue().reference();
            if (reference != null && reference.to().contains(element.rule().name()) && reference.by().equals(name)) {
                element.setKeyAttribute(name);
                return NewText.plain(element.attributes().get(name), null);
            }
        }
        SlotRead read = element.idRead();
        if (element.rule().id() != null && element.rule().id().from() != null && read != null && read.present()) {
            return NewText.plain(read.value(), null);
        }
        return element.id();
    }

    private ReadElement end(ReadElement relation, SlotRead read, String end) {
        if (read == null || !read.present() || read.value() == null) {
            family.report(FindingCodes.DANGLING_REFERENCE, FindingSeverity.WARNING,
                    "This " + relation.rule().type() + " has no " + end + ", so it is not read as a relation.", relation.entry().own());
            return null;
        }
        String key = NewText.plain(read.value(), null);
        ReadElement found = null;
        for (ReadElement element : elements) {
            if (!element.isRelation() && element.key().equals(key)) {
                found = element;
                break;
            }
        }
        if (found == null) {
            family.report(FindingCodes.DANGLING_REFERENCE, FindingSeverity.WARNING,
                    "The " + end + " '" + key + "' of this " + relation.rule().type() + " names no element, so it is not read as a relation.",
                    read.span() != null ? read.span() : relation.entry().own());
        }
        return found;
    }

    private void checkReferences(ReadElement element) {
        for (Map.Entry<String, AttributeBinding> attribute : element.rule().attributes().entrySet()) {
            String name = attribute.getKey();
            ReferenceBinding reference = attribute.getValue().reference();
            if (reference == null || reference.to().contains(element.rule().name())) {
                continue;
            }
            SlotRead read = element.slots().get(name);
            if (read == null || !read.present()) {
                continue;
            }
            List<String> names = new ArrayList<>();
            if (element.attributes().get(name) instanceof List<?> list) {
                for (Object value : list) {
                    names.add(NewText.plain(value, null));
                }
            } else {
                names.add(NewText.plain(read.value(), null));
            }
            for (String key : names) {
                if (referencedBy(reference, key) == null) {
                    family.report(FindingCodes.DANGLING_REFERENCE, FindingSeverity.WARNING,
                            "'" + key + "' in " + name + " names no " + String.join(" or ", reference.to()) + ".",
                            read.span() != null ? read.span() : element.entry().own());
                }
            }
        }
    }

    /** The element a reference names by {@code key}, or null. */
    public ReadElement referencedBy(ReferenceBinding reference, String key) {
        for (ReadElement element : elements) {
            if (reference.to().contains(element.rule().name()) && NewText.plain(element.attributes().get(reference.by()), null).equals(key)) {
                return element;
            }
        }
        return null;
    }

    // ---- the public model ----

    public FblModel toModel() {
        List<FblElement> model = new ArrayList<>();
        for (ReadElement e : elements) {
            model.add(new FblElement(
                    e.id(),
                    e.idStored(),
                    e.rule().type(),
                    e.rule().name(),
                    e.isRelation(),
                    Collections.unmodifiableMap(new LinkedHashMap<>(e.attributes())),
                    e.parent() != null ? e.parent().id() : null,
                    e.parent() == null || e.rule().parent() == null ? null : e.rule().parent().slot(),
                    e.sourceElement() != null ? e.sourceElement().id() : null,
                    e.targetElement() != null ? e.targetElement().id() : null,
                    e.entry().own(),
                    e.line()));
        }
        return new FblModel(model, findings, unreadable != null, views, resources);
    }
}
