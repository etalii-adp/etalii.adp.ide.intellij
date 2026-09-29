package etalii.adp.core.settings;

/** Where a tool comes from (FR-007, FR-016, research R9). */
public sealed interface ToolOrigin {

    /** As the page shows it. */
    String describe();

    /** Built into this plug-in as a module. */
    record Module(String pluginId) implements ToolOrigin {

        @Override
        public String describe() {
            return "Built into ADP";
        }
    }

    /** Registered by another plug-in. */
    record OtherPlugin(String pluginId, String pluginName) implements ToolOrigin {

        @Override
        public String describe() {
            return "From plug-in " + pluginName;
        }
    }

    /** Interpreted from a DISL specification copied out of etalii.adp and shipped inside the plug-in. */
    record BundledSpecification(String name, String dislVersion, String sourceRevision) implements ToolOrigin {

        @Override
        public String describe() {
            return "DISL specification " + name + " (DISL " + dislVersion + "), copied from etalii.adp at " + sourceRevision;
        }
    }
}
