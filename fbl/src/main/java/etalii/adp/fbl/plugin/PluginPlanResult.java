package etalii.adp.fbl.plugin;

import java.util.List;

/** What {@code plan} delivers: splices, or the sentence the host shows when the change is refused. */
public sealed interface PluginPlanResult {

    record Planned(List<PluginSplice> splices) implements PluginPlanResult {

        public Planned {
            splices = List.copyOf(splices);
        }
    }

    record Refused(String reason) implements PluginPlanResult {
    }
}
