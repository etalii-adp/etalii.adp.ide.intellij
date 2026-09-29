package etalii.adp.core.settings;

import java.util.List;
import java.util.Set;

/**
 * One installed tool as the ADP page shows it (FR-007).
 *
 * @param fileTypes the extensions it handles, sorted
 * @param problems each problem found while loading; empty when it loaded
 * @param conflictsWith the ids of other tools sharing a file type (FR-017)
 * @param unfollowed the canvas options its definition fixes (FR-012)
 */
public record ToolInfo(String id, String name, List<String> fileTypes, String version, ToolOrigin origin, List<String> problems, boolean on,
        List<String> conflictsWith, Set<CanvasOption> unfollowed) {

    public enum Status {
        LOADED("Loaded"), NOT_LOADED("Not loaded");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public ToolInfo {
        fileTypes = List.copyOf(fileTypes);
        problems = List.copyOf(problems);
        conflictsWith = List.copyOf(conflictsWith);
        unfollowed = Set.copyOf(unfollowed);
    }

    /** Decided when the provider is created; it does not change during a session. */
    public Status status() {
        return problems.isEmpty() ? Status.LOADED : Status.NOT_LOADED;
    }
}
