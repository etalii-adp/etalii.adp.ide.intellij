package etalii.adp.core.settings;

/** Where a designer comes from (FR-007, FR-016, research R9). */
public sealed interface DesignerOrigin {

    /** As the page shows it. */
    String describe();

    /** Built into this plug-in as a module. */
    record Module(String pluginId) implements DesignerOrigin {

        @Override
        public String describe() {
            return "Built into ADP";
        }
    }

    /** Registered by another plug-in. */
    record OtherPlugin(String pluginId, String pluginName) implements DesignerOrigin {

        @Override
        public String describe() {
            return "From plug-in " + pluginName;
        }
    }

    /** Interpreted from a DEDL definition copied out of etalii.adp and shipped inside the plug-in. */
    record BundledDefinition(String name, String dedlVersion, String sourceRevision) implements DesignerOrigin {

        @Override
        public String describe() {
            return "DEDL definition " + name + " (DEDL " + dedlVersion + "), copied from etalii.adp at " + sourceRevision;
        }
    }
}
