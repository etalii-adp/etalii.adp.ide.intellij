package etalii.adp.fbl.rule;

import java.util.Map;

import etalii.adp.fbl.document.BlockRule;
import etalii.adp.fbl.document.Rule;

/**
 * An entry a rule's selector or line matched, with the captures or groups the match bound.
 *
 * @param rule the rule that matched, or null when a block rule did or none is meant
 * @param block the block rule that matched, or null when a rule did
 * @param captures the captures or groups by name, in the order the match bound them
 */
public record Candidate(Rule rule, BlockRule block, Entry entry, Map<String, String> captures) {

    public Candidate withRule(Rule value) {
        return new Candidate(value, block, entry, captures);
    }
}
