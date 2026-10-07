package etalii.adp.fbl.plan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import etalii.adp.fbl.Edit;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.document.AttributeBinding;
import etalii.adp.fbl.document.ParentBinding;
import etalii.adp.fbl.document.ReferenceBinding;
import etalii.adp.fbl.document.RemoveSettings;
import etalii.adp.fbl.document.Rule;
import etalii.adp.fbl.document.Slot;
import etalii.adp.fbl.rule.BodyReading;
import etalii.adp.fbl.rule.InsertRequest;
import etalii.adp.fbl.rule.ReadElement;
import etalii.adp.fbl.rule.RefusedException;
import etalii.adp.fbl.rule.SlotChange;
import etalii.adp.fbl.rule.SlotRead;
import etalii.adp.fbl.rule.Word;
import etalii.adp.fbl.text.Span;

/**
 * Plans a model change as one edit (FBL §6.4): the splices of every attribute set, element added
 * or removed, in the body's own conventions, or the reason the change cannot be made. Planning
 * writes nothing; {@link etalii.adp.fbl.history.OpenBody} applies what it plans.
 */
public final class EditPlanner {

    private EditPlanner() {
    }

    public static PlanResult plan(BodyReading reading, ModelChange change) {
        if (reading.unreadable() != null) {
            return new PlanResult.Refused("The file could not be read, so it is never written.");
        }
        String readOnly = reading.binding().readOnly();
        if (readOnly != null) {
            return new PlanResult.Refused(!readOnly.isEmpty() ? readOnly : "This file is read-only.");
        }
        Plan plan = new Plan(reading);
        try {
            switch (change) {
                case ModelChange.Save _ -> {
                }
                case ModelChange.Set set -> planSet(plan, set);
                case ModelChange.Add add -> planAdd(plan, add);
                case ModelChange.Remove remove -> planRemove(plan, remove);
                case ModelChange.Place _, ModelChange.Identify _ ->
                    throw new IllegalArgumentException("Placements and stored ids are edits of the registration, not of the body.");
            }
            return new PlanResult.Planned(new Edit(plan.ordered(), plan.snapshot()));
        } catch (RefusedException refused) {
            return new PlanResult.Refused(refused.getMessage());
        }
    }

    private static ReadElement element(BodyReading reading, String id) {
        ReadElement element = reading.find(id);
        if (element == null) {
            throw new IllegalArgumentException("The model has no element or relation '" + id + "'.");
        }
        return element;
    }

    private static void refuseReadOnly(Rule rule, String gesture) {
        String reason = rule.readOnly();
        if (reason != null) {
            Plan.refuse(!reason.isEmpty() ? reason : "A " + rule.type() + " cannot be " + gesture + " in this file.");
        }
    }

    // ---- set ----

    private static void planSet(Plan plan, ModelChange.Set set) {
        BodyReading reading = plan.reading();
        ReadElement element = element(reading, set.id());
        refuseReadOnly(element.rule(), "changed");
        List<SlotChange> changes = new ArrayList<>();
        String newKey = null;
        for (Map.Entry<String, Object> requested : set.attributes().entrySet()) {
            String attribute = requested.getKey();
            Object value = requested.getValue();
            Rule rule;
            AttributeBinding binding;
            SlotRead read;
            if (element.isRelation() && (attribute.equals("source") || attribute.equals("target"))) {
                binding = attribute.equals("source") ? element.rule().source() : element.rule().target();
                ReadElement end = element(reading, NewText.plain(value, null));
                value = end.key();
                SlotRead endRead = attribute.equals("source") ? element.sourceRead() : element.targetRead();
                read = endRead != null ? endRead : SlotRead.ABSENT;
                rule = element.rule();
            } else {
                Bound bound = binding(reading, element, attribute);
                rule = bound.rule();
                binding = bound.binding();
                SlotRead known = element.slots().get(attribute);
                read = binding == null ? SlotRead.ABSENT
                        : rule == element.rule() && known != null ? known
                        : reading.readSlot(element.candidate().withRule(rule), binding, element);
            }
            if (binding == null) {
                Plan.refuse("A " + element.rule().type() + " keeps no '" + attribute + "' in this file.");
                return;
            }
            if (binding.isComputed() || binding.parent() != null || !read.writable()) {
                Plan.refuse(read.reason() != null && !read.reason().isEmpty() ? read.reason()
                        : "The " + attribute + " of a " + element.rule().type() + " cannot be changed in this file.");
            }
            boolean empty = NewText.isEmpty(value) || (binding.flag() && Boolean.FALSE.equals(value));
            if (empty && !binding.flag() && "refuse".equals(binding.empty())) {
                Plan.refuse("The " + attribute + " of a " + element.rule().type() + " cannot be empty.");
            }
            if (!read.present() && !empty && !binding.flag() && "refuse".equals(binding.absent().get(reading.family().familyName()))) {
                Plan.refuse("The " + reading.binding().name() + " file has no \"" + slotName(binding) + "\" to rewrite.");
            }
            if (empty && !read.present() && !binding.flag()) {
                continue;
            }
            if (!empty && isKey(element, attribute, binding)) {
                String key = NewText.plain(value, null);
                if (!key.equals(element.key())) {
                    if (reading.elements().stream().anyMatch(e -> e != element && e.rule() == element.rule() && e.key().equals(key))) {
                        Plan.refuse("Another " + element.rule().type() + " is already named '" + key + "'.");
                    }
                    newKey = key;
                }
            }
            changes.add(new SlotChange(attribute, binding, rule, read, value, empty));
        }
        if (!changes.isEmpty()) {
            reading.family().write(plan, element, changes);
        }
        if (newKey != null) {
            rewriteReferences(plan, element, newKey);
        }
        if (element.rule().snapshotUndo()) {
            plan.setSnapshot(true);
        }
    }

    private static String slotName(Slot slot) {
        return slot.key() != null ? slot.key()
                : slot.xmlAttribute() != null ? slot.xmlAttribute()
                : slot.group() != null ? slot.group()
                : slot.capture() != null ? slot.capture()
                : slot.child() != null ? slot.child()
                : "value";
    }

    /**
     * A rule and its binding of one attribute.
     *
     * @param binding the binding, or null when no rule stores the attribute
     */
    private record Bound(Rule rule, AttributeBinding binding) {
    }

    /**
     * The binding that stores {@code attribute} for the element: its own rule's, else that of
     * another rule that matches the same entry (a Moment given an end is written by the Period
     * rule's binding, FBL §5.1).
     */
    private static Bound binding(BodyReading reading, ReadElement element, String attribute) {
        AttributeBinding own = element.rule().attribute(attribute);
        if (own != null) {
            return new Bound(element.rule(), own);
        }
        for (Rule rule : reading.binding().allRules()) {
            AttributeBinding other = rule.attribute(attribute);
            if (rule == element.rule() || other == null) {
                continue;
            }
            if (reading.family().candidates(rule).stream().anyMatch(c -> c.entry() == element.entry())) {
                return new Bound(rule, other);
            }
        }
        return new Bound(element.rule(), null);
    }

    private static boolean isKey(ReadElement element, String attribute, AttributeBinding binding) {
        if (attribute.equals(element.keyAttribute())) {
            return true;
        }
        return element.keyAttribute() == null && element.rule().id() != null && element.rule().id().from() != null
                && sameSlot(element.rule().id().from(), binding);
    }

    private static boolean sameSlot(Slot a, Slot b) {
        return Objects.equals(a.key(), b.key()) && Objects.equals(a.xmlAttribute(), b.xmlAttribute()) && Objects.equals(a.group(), b.group())
                && a.text() == b.text() && Objects.equals(a.child(), b.child()) && Objects.equals(a.word(), b.word())
                && Objects.equals(a.capture(), b.capture());
    }

    /**
     * A rename (FBL §5.7): every reference to the old value is rewritten in the same edit, one
     * {@code rewrite-reference} splice each, word by word inside a group.
     */
    private static void rewriteReferences(Plan plan, ReadElement renamed, String newKey) {
        BodyReading reading = plan.reading();
        String oldKey = renamed.key();
        for (ReadElement other : reading.elements()) {
            if (other.isRelation()) {
                if (other.sourceElement() == renamed) {
                    rewrite(plan, other.sourceRead(), oldKey, newKey);
                }
                if (other.targetElement() == renamed) {
                    rewrite(plan, other.targetRead(), oldKey, newKey);
                }
            }
            for (Map.Entry<String, AttributeBinding> attribute : other.rule().attributes().entrySet()) {
                String name = attribute.getKey();
                ReferenceBinding reference = attribute.getValue().reference();
                if (reference == null || !reference.to().contains(renamed.rule().name())) {
                    continue;
                }
                if (other == renamed && name.equals(renamed.keyAttribute())) {
                    continue;
                }
                SlotRead read = other.slots().get(name);
                if (read == null || !read.present()) {
                    continue;
                }
                List<Word> words = read.words();
                if (words != null && (words.size() > 1 || (words.size() == 1 && other.attributes().get(name) instanceof List<?>))) {
                    for (Word word : words) {
                        if (!word.text().equals(oldKey)) {
                            continue;
                        }
                        rewrite(plan, new SlotRead(word.text(), word.span(), true, true).withQuote(word.quoted() ? "\"" : null), oldKey, newKey);
                    }
                    continue;
                }
                rewrite(plan, read, oldKey, newKey);
            }
        }
    }

    /** @param read how the reference was read, or null */
    private static void rewrite(Plan plan, SlotRead read, String oldKey, String newKey) {
        if (read == null || !read.present() || read.span() == null || !NewText.plain(read.value(), null).equals(oldKey) || plan.touches(read.span())) {
            return;
        }
        plan.add(SpliceOperation.REWRITE_REFERENCE, read.span(), plan.reading().family().format(read, null, newKey));
    }

    // ---- add ----

    private static void planAdd(Plan plan, ModelChange.Add add) {
        BodyReading reading = plan.reading();
        List<Rule> rules = reading.binding().allRules().stream().filter(r -> r.type().equals(add.type())).toList();
        if (rules.isEmpty()) {
            Plan.refuse("This file has no place for a " + add.type() + ".");
        }
        Rule rule = rules.stream().filter(r -> r.insert() != null).findFirst().orElse(null);
        if (rule == null) {
            String text = rules.get(0).readOnly();
            Plan.refuse(text != null && !text.isEmpty() ? text : "A " + add.type() + " cannot be added to this file.");
            return;
        }
        refuseReadOnly(rule, "added");
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> attribute : add.attributes().entrySet()) {
            String name = attribute.getKey();
            if (rule.isRelation() && (name.equals("source") || name.equals("target"))) {
                continue;
            }
            values.put(name, attribute.getValue());
        }
        String when = rule.insert().when();
        if (when != null && !reading.insertAllowed(when, values)) {
            Plan.refuse("This " + add.type() + " cannot be added to the file.");
        }
        ReadElement source = null;
        ReadElement target = null;
        if (rule.isRelation()) {
            source = end(reading, add, "source");
            target = end(reading, add, "target");
        }
        ReadElement parent = add.parentId() != null ? element(reading, add.parentId()) : null;
        ParentBinding containment = rule.parent();
        if (parent != null && containment != null && !containment.rules().contains(parent.rule().name())) {
            Plan.refuse("A " + add.type() + " cannot be placed inside a " + parent.rule().type() + ".");
        }
        String id = add.id();
        if (rule.id() != null && rule.id().from() != null && id != null
                && reading.elements().stream().anyMatch(e -> e.rule().id() != null && e.rule().id().from() != null && e.id().equals(id))) {
            Plan.refuse("Another element already has the id '" + id + "'.");
        }
        reading.family().insert(plan, new InsertRequest(rule, add.id(), values, parent, source, target));
        if (rule.snapshotUndo()) {
            plan.setSnapshot(true);
        }
    }

    private static ReadElement end(BodyReading reading, ModelChange.Add add, String end) {
        Object value = add.attributes().get(end);
        if (value == null) {
            Plan.refuse("A " + add.type() + " needs a " + end + ".");
        }
        String id = NewText.plain(value, null);
        ReadElement element = reading.find(id);
        if (element == null || element.isRelation()) {
            Plan.refuse("The " + end + " '" + id + "' names no element.");
        }
        return element;
    }

    // ---- remove ----

    private static void planRemove(Plan plan, ModelChange.Remove remove) {
        BodyReading reading = plan.reading();
        ReadElement element = element(reading, remove.id());
        refuseReadOnly(element.rule(), "removed");
        RemoveSettings settings = element.rule().remove();
        if (settings == null) {
            Plan.refuse("A " + element.rule().type() + " cannot be removed from this file.");
            return;
        }
        // In the order they were found: the family removes equal starts in that order.
        Set<ReadElement> removed = new LinkedHashSet<>();
        removed.add(element);
        for (ReadElement other : reading.elements()) {
            if (other == element || !settings.cascade().contains(other.rule().name())) {
                continue;
            }
            if (references(other, element)) {
                removed.add(other);
            }
        }
        List<ReadElement> outermost = new ArrayList<>(removed.stream()
                .filter(r -> removed.stream().noneMatch(o -> o != r && contains(o.entry().removalSpan(), r.entry().removalSpan())))
                .toList());
        outermost.sort(Comparator.comparingInt((ReadElement r) -> r.entry().own().start()).reversed());
        for (ReadElement target : outermost) {
            reading.family().remove(plan, target, removed);
        }
        if (removed.stream().anyMatch(r -> r.rule().snapshotUndo())) {
            plan.setSnapshot(true);
        }
    }

    private static boolean contains(Span outer, Span inner) {
        return outer.start() <= inner.start() && inner.end() <= outer.end() && !outer.equals(inner);
    }

    private static boolean references(ReadElement other, ReadElement element) {
        if (other.isRelation() && (other.sourceElement() == element || other.targetElement() == element)) {
            return true;
        }
        for (Map.Entry<String, AttributeBinding> attribute : other.rule().attributes().entrySet()) {
            ReferenceBinding reference = attribute.getValue().reference();
            if (reference == null || !reference.to().contains(element.rule().name()) || other == element) {
                continue;
            }
            Object value = other.attributes().get(attribute.getKey());
            boolean names = value instanceof List<?> list
                    ? list.stream().anyMatch(v -> NewText.plain(v, null).equals(element.key()))
                    : NewText.plain(value, null).equals(element.key());
            if (names) {
                return true;
            }
        }
        return false;
    }
}
