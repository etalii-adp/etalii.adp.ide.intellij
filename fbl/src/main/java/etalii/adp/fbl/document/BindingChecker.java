package etalii.adp.fbl.document;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import etalii.adp.fbl.expression.CelCompiler;
import etalii.adp.fbl.expression.CelContext;
import etalii.adp.fbl.expression.CelException;
import etalii.adp.fbl.expression.RegexSubset;

/** The checks of FBL §14.1 steps 4 to 6 that need no DISL specification. */
final class BindingChecker {

    private BindingChecker() {
    }

    static void check(FblBinding binding, String pointer, List<Problem> problems) {
        List<Rule> rules = binding.allRules();
        Set<String> names = new HashSet<>();
        Set<String> ruleNames = new HashSet<>();
        rules.forEach(r -> ruleNames.add(r.name()));
        binding.blocks().forEach(b -> ruleNames.add(b.name()));
        Set<String> fileRules = new HashSet<>();
        binding.body().files().stream().map(FileRule::name).filter(Objects::nonNull).forEach(fileRules::add);

        for (int i = 0; i < binding.blocks().size(); i++) {
            BlockRule block = binding.blocks().get(i);
            String at = pointer + "/blocks/" + i;
            if (!names.add(block.name())) {
                problems.add(new Problem(at + "/name", ProblemSeverity.ERROR, "The rule name '" + block.name() + "' is used twice."));
            }
            checkRegex(block.line(), at + "/line", problems);
            checkWithin(block.within(), ruleNames, at + "/within", problems);
        }

        int elementCount = binding.elements().size();
        for (int i = 0; i < rules.size(); i++) {
            Rule rule = rules.get(i);
            String at = rule.isRelation() ? pointer + "/relations/" + (i - elementCount) : pointer + "/elements/" + i;
            if (!Names.isName(rule.name())) {
                problems.add(new Problem(at + "/name", ProblemSeverity.ERROR, "'" + rule.name() + "' is not a valid rule name."));
            }
            if (!names.add(rule.name())) {
                problems.add(new Problem(at + "/name", ProblemSeverity.ERROR, "The rule name '" + rule.name() + "' is used twice."));
            }
            if ((rule.at() == null) == (rule.line() == null) && binding.plugin() == null) {
                problems.add(new Problem(at, ProblemSeverity.ERROR, "A rule has exactly one of 'at' and 'line'."));
            }
            if (rule.line() != null) {
                checkRegex(rule.line(), at + "/line", problems);
            }
            checkWithin(rule.within(), ruleNames, at + "/within", problems);
            for (String file : rule.files()) {
                if (!fileRules.contains(file)) {
                    problems.add(new Problem(at + "/files", ProblemSeverity.ERROR, "No file rule is named '" + file + "'."));
                }
            }
            if (rule.parent() != null) {
                for (String p : rule.parent().rules()) {
                    require(p, rules, at + "/parent/rules", problems);
                }
            }
            if (rule.remove() != null) {
                for (String c : rule.remove().cascade()) {
                    require(c, rules, at + "/remove/cascade", problems);
                }
            }
            CelContext context = rule.at() != null ? CelContext.TREE : CelContext.LINES;
            compile(rule.when(), context, at + "/when", problems);
            compile(rule.insert() == null ? null : rule.insert().when(), CelContext.INSERT, at + "/insert/when", problems);
            if (rule.id() != null && rule.id().from() != null) {
                checkSlot(rule.id().from(), context, at + "/id/from", problems);
            }
            if (rule.id() != null && rule.id().sidecarKey() != null) {
                compile(rule.id().sidecarKey(), context, at + "/id/sidecar/key", problems);
            }
            if (rule.isRelation() && (rule.source() == null || rule.target() == null)) {
                problems.add(new Problem(at, ProblemSeverity.ERROR, "A relation rule has a source and a target."));
            }
            for (Map.Entry<String, AttributeBinding> entry : rule.attributes().entrySet()) {
                AttributeBinding attribute = entry.getValue();
                String a = at + "/attributes/" + FblDocumentLoader.escape(entry.getKey());
                checkSlot(attribute, context, a, problems);
                if (attribute.reference() != null) {
                    for (String to : attribute.reference().to()) {
                        require(to, rules, a + "/reference/to", problems);
                    }
                }
                if (attribute.override() != null) {
                    checkSlot(attribute.override(), context, a + "/override", problems);
                }
                if (countSlots(attribute) != 1) {
                    problems.add(new Problem(a, ProblemSeverity.ERROR, "An attribute binding names exactly one slot (FBL §3.3)."));
                }
            }
            if (rule.source() != null) {
                checkSlot(rule.source(), context, at + "/source", problems);
            }
            if (rule.target() != null) {
                checkSlot(rule.target(), context, at + "/target", problems);
            }
            if (rule.insert() != null && rule.insert().constant() != null) {
                checkConstant(binding, rule, at + "/insert/constant", problems);
            }
        }

        if (binding.plugin() == null) {
            if (!binding.body().isFolder() && binding.body().family() == null) {
                problems.add(new Problem(pointer + "/body/family", ProblemSeverity.ERROR, "A declared file body names its family."));
            }
            if (rules.isEmpty()) {
                problems.add(new Problem(pointer, ProblemSeverity.ERROR, "A declared binding has at least one element or relation rule."));
            }
        } else if (!rules.isEmpty()) {
            problems.add(new Problem(pointer, ProblemSeverity.ERROR, "A binding read by a plugin has no rules; its model is what the plugin reads."));
        }
        Claims claims = binding.claims();
        if (claims.shared() && claims.marker() == null && !claims.registrationOnly()) {
            problems.add(new Problem(pointer + "/claims", ProblemSeverity.ERROR, "A shared claim has a marker or is registrationOnly."));
        }
        if (claims.readings().values().stream().filter(ReadingClaim::bare).count() > 1) {
            problems.add(new Problem(pointer + "/claims/readings", ProblemSeverity.ERROR, "At most one reading is bare."));
        }
        if (binding.comment() != null) {
            checkRegex(binding.comment(), pointer + "/comment", problems);
        }
        if (binding.header() != null && binding.header().line() != null) {
            checkRegex(binding.header().line(), pointer + "/header/line", problems);
        }
        if (claims.marker() != null && claims.marker().pattern() != null) {
            checkRegex(claims.marker().pattern(), pointer + "/claims/marker/pattern", problems);
        }
    }

    /**
     * FBL 0.8 §14.1 step 6: an insert's constant is given only in the yaml and json families and never beside
     * {@code emit}, its values are scalars, and none of its keys is one the same rule writes, by a writable attribute
     * binding, {@code id.from}, {@code source} or {@code target}, since a place has one writer (§3.3). A read-only
     * binding may read its key.
     */
    private static void checkConstant(FblBinding binding, Rule rule, String pointer, List<Problem> problems) {
        List<Family> families = familiesOf(binding, rule);
        if (families.isEmpty() || families.stream().anyMatch(f -> f != Family.YAML && f != Family.JSON)) {
            problems.add(new Problem(pointer, ProblemSeverity.ERROR, "An insert's constant is given only in the yaml and json families (FBL 0.8 6.2)."));
        }
        if (rule.insert().emit() != null) {
            problems.add(new Problem(pointer, ProblemSeverity.ERROR, "An insert has a constant or an emit, not both (FBL 0.8 6.2)."));
        }
        for (Map.Entry<String, Object> member : rule.insert().constant().entrySet()) {
            String at = pointer + "/" + FblDocumentLoader.escape(member.getKey());
            if (member.getValue() == InsertSettings.NOT_A_SCALAR) {
                problems.add(new Problem(at, ProblemSeverity.ERROR, "A constant's value is a string, a number, a boolean or null."));
            }
            String writer = writerOf(rule, member.getKey());
            if (writer != null) {
                problems.add(new Problem(at, ProblemSeverity.ERROR,
                        "The constant's key '" + member.getKey() + "' is written by the rule's " + writer + " too, and a place has one writer (FBL 0.8 14.1)."));
            }
        }
    }

    /** What of a rule writes the key of its own entry, or null when nothing does. */
    private static String writerOf(Rule rule, String key) {
        if (rule.id() != null && writes(rule.id().from(), key)) {
            return "id.from";
        }
        if (writes(rule.source(), key)) {
            return "source";
        }
        if (writes(rule.target(), key)) {
            return "target";
        }
        if (rule.readOnly() != null) {
            return null;
        }
        for (Map.Entry<String, AttributeBinding> attribute : rule.attributes().entrySet()) {
            if (writes(attribute.getValue(), key) && attribute.getValue().readOnly() == null) {
                return "attribute '" + attribute.getKey() + "'";
            }
        }
        return null;
    }

    private static boolean writes(Slot slot, String key) {
        return slot != null && key.equals(slot.key()) && slot.child() == null;
    }

    /** The families a rule reads: those of the file rules it names, else the body's, else those of every file rule. */
    private static List<Family> familiesOf(FblBinding binding, Rule rule) {
        if (!rule.files().isEmpty()) {
            return binding.body().files().stream().filter(f -> f.name() != null && rule.files().contains(f.name())).map(FileRule::family).filter(Objects::nonNull).toList();
        }
        if (binding.body().family() != null) {
            return List.of(binding.body().family());
        }
        return binding.body().files().stream().map(FileRule::family).filter(Objects::nonNull).toList();
    }

    private static int countSlots(Slot s) {
        return (s.key() == null ? 0 : 1) + (s.xmlAttribute() == null ? 0 : 1) + (s.text() ? 1 : 0) + (s.group() == null ? 0 : 1)
                + (s.parent() == null ? 0 : 1) + (s.capture() == null ? 0 : 1) + (s.value() == null ? 0 : 1);
    }

    private static void checkSlot(Slot slot, CelContext context, String pointer, List<Problem> problems) {
        if (slot.value() != null) {
            compile(slot.value(), context, pointer + "/value", problems);
        }
        if (slot.word() != null) {
            checkRegex(slot.word(), pointer + "/word", problems);
        }
    }

    private static void require(String name, List<Rule> rules, String pointer, List<Problem> problems) {
        if (rules.stream().noneMatch(r -> r.name().equals(name))) {
            problems.add(new Problem(pointer, ProblemSeverity.ERROR, "No rule is named '" + name + "'."));
        }
    }

    private static void checkWithin(List<String> within, Set<String> names, String pointer, List<Problem> problems) {
        if (within == null) {
            return;
        }
        for (String w : within) {
            if (!w.equals("^") && !names.contains(w)) {
                problems.add(new Problem(pointer, ProblemSeverity.ERROR, "No rule is named '" + w + "'."));
            }
        }
    }

    private static void checkRegex(String expression, String pointer, List<Problem> problems) {
        String problem = RegexSubset.check(expression);
        if (problem != null) {
            problems.add(new Problem(pointer, ProblemSeverity.ERROR, problem));
        }
    }

    private static void compile(String expression, CelContext context, String pointer, List<Problem> problems) {
        if (expression == null) {
            return;
        }
        try {
            CelCompiler.compile(expression, context);
        } catch (CelException e) {
            problems.add(new Problem(pointer, ProblemSeverity.ERROR, e.getMessage()));
        }
    }
}
