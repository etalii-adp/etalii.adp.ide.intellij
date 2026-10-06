package etalii.adp.fbl.document;

import java.util.List;

/**
 * A block rule of a blocks body (FBL §4.7).
 *
 * @param within the blocks this one must lie within, or null when the rule does not say
 * @param view the capture that names the view this block defines, or null
 */
public record BlockRule(String name, String line, List<String> within, boolean caseInsensitive, String view) {

    public BlockRule {
        within = within == null ? null : List.copyOf(within);
    }
}
