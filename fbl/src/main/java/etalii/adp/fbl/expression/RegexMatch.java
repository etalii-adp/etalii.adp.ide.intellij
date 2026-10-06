package etalii.adp.fbl.expression;

import java.util.List;
import java.util.Map;

/**
 * A successful match of a {@link BoundedRegex}: the whole match and its named groups. Every index
 * is a char index into the input that was matched, not a byte offset.
 */
public final class RegexMatch {

    /**
     * A named group, or the whole match.
     *
     * @param success whether the group took part in the match; when false the value is empty and both indices are 0
     * @param value   the text the group matched
     * @param start   the char index in the input where the value starts
     * @param end     the char index in the input just after the value
     */
    public record Group(boolean success, String value, int start, int end) {

        static final Group NONE = new Group(false, "", 0, 0);

        public int length() {
            return end - start;
        }
    }

    private final Group whole;
    private final List<String> groupNames;
    private final Map<String, Group> groups;

    RegexMatch(Group whole, List<String> groupNames, Map<String, Group> groups) {
        this.whole = whole;
        this.groupNames = groupNames;
        this.groups = groups;
    }

    /** The whole match as a group; it always took part. */
    public Group whole() {
        return whole;
    }

    public String value() {
        return whole.value();
    }

    public int start() {
        return whole.start();
    }

    public int end() {
        return whole.end();
    }

    public int length() {
        return whole.length();
    }

    /** The names of the expression's named groups as the binding spells them, in the order they first appear. */
    public List<String> groupNames() {
        return groupNames;
    }

    /** The group of that name, never null: a group that did not take part, or a name the expression does not have, has {@code success} false. */
    public Group group(String name) {
        return groups.getOrDefault(name, Group.NONE);
    }
}
