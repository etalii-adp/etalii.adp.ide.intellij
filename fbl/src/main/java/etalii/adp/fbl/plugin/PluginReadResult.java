package etalii.adp.fbl.plugin;

import java.util.List;

import etalii.adp.fbl.FblElement;
import etalii.adp.fbl.Finding;

/** What {@code read} delivers: the elements and relations with their source spans, the findings, and whether the body is unreadable. */
public record PluginReadResult(List<FblElement> elements, List<Finding> findings, boolean unreadable) {

    public PluginReadResult {
        elements = List.copyOf(elements);
        findings = List.copyOf(findings);
    }
}
