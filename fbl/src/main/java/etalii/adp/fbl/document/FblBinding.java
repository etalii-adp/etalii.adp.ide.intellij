package etalii.adp.fbl.document;

import java.util.List;
import java.util.stream.Stream;

/**
 * One binding (FBL §3): how one format maps to a language's model.
 *
 * @param title the binding's title, or null
 * @param plugin null for a declared reader; the plugin otherwise (FBL §11)
 * @param readOnly null when the body may be written; the reason (possibly empty) when it is read-only
 * @param header the body's header, or null
 * @param comment the comment marker of a lines or blocks body, or null
 * @param template the binding's template, or null
 */
public record FblBinding(
        String name,
        String title,
        Claims claims,
        BodySettings body,
        PluginReader plugin,
        String readOnly,
        TextDefaults text,
        HeaderSettings header,
        String comment,
        boolean reportUnmatched,
        List<BlockRule> blocks,
        List<Rule> elements,
        List<Rule> relations,
        RegistrationSettings registration,
        TemplateSettings template) {

    public FblBinding {
        blocks = List.copyOf(blocks);
        elements = List.copyOf(elements);
        relations = List.copyOf(relations);
    }

    /** Elements before relations, in binding order: the order rules are offered an entry (FBL §5.1). */
    public List<Rule> allRules() {
        return Stream.concat(elements.stream(), relations.stream()).toList();
    }

    /** The rule of that name, or null. */
    public Rule findRule(String ruleName) {
        return allRules().stream().filter(r -> r.name().equals(ruleName)).findFirst().orElse(null);
    }

    /** The block rule of that name, or null. */
    public BlockRule findBlock(String blockName) {
        return blocks.stream().filter(b -> b.name().equals(blockName)).findFirst().orElse(null);
    }
}
