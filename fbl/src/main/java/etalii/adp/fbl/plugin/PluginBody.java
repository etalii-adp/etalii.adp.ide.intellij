package etalii.adp.fbl.plugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

import etalii.adp.fbl.Edit;
import etalii.adp.fbl.FblModel;
import etalii.adp.fbl.Finding;
import etalii.adp.fbl.FindingCodes;
import etalii.adp.fbl.FindingSeverity;
import etalii.adp.fbl.SourceLocation;
import etalii.adp.fbl.Splice;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.history.SplicedFile;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.plan.PlanResult;

/**
 * A file body read by a persistence plugin (FBL §11.3): the plugin reads and plans, and this host
 * side applies the splices, keeps the history, checks drift and saves, exactly as for a declared
 * body. Without the plugin the body opens read-only with DISL's {@code std.pluginMissing} (FBL §15.1).
 */
public final class PluginBody extends SplicedFile {

    private final FblBinding binding;
    private final String fileName;
    private final PersistencePlugin plugin;
    private PluginReadResult last;

    private PluginBody(byte[] bytes, FblBinding binding, PersistencePlugin plugin, String fileName) {
        super(bytes);
        this.binding = binding;
        this.fileName = fileName;
        this.plugin = plugin != null && plugin.id().equals(binding.plugin().plugin()) ? plugin : null;
        last = readNow();
    }

    public FblBinding binding() {
        return binding;
    }

    public String fileName() {
        return fileName;
    }

    public FblModel model() {
        return new FblModel(last.elements(), last.findings(), last.unreadable());
    }

    /** Read-only without the plugin, when the plugin reports the body unreadable, or when the binding is read-only. */
    public boolean isReadOnly() {
        return plugin == null || last.unreadable() || binding.readOnly() != null;
    }

    /** {@link #open(byte[], FblBinding, PersistencePlugin, String)} with the file name {@code body}. */
    public static PluginBody open(byte[] bytes, FblBinding binding, PersistencePlugin plugin) {
        return open(bytes, binding, plugin, "body");
    }

    /**
     * Opens a body whose binding names a plugin. {@code plugin} is the one the caller has
     * installed, or null; a plugin with another id counts as missing.
     */
    public static PluginBody open(byte[] bytes, FblBinding binding, PersistencePlugin plugin, String fileName) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(binding, "binding");
        if (binding.plugin() == null) {
            throw new IllegalArgumentException("The binding '" + binding.name() + "' is read by its declared rules, not by a plugin.");
        }
        return new PluginBody(bytes, binding, plugin, fileName);
    }

    public PlanResult plan(ModelChange change) {
        Objects.requireNonNull(change, "change");
        if (plugin == null) {
            return new PlanResult.Refused(missingReason());
        }
        if (last.unreadable()) {
            return new PlanResult.Refused("The file could not be read, so it is never written.");
        }
        String reason = binding.readOnly();
        if (reason != null) {
            return new PlanResult.Refused(!reason.isEmpty() ? reason : "This file is read-only.");
        }
        if (change instanceof ModelChange.Save) {
            return new PlanResult.Planned(new Edit(List.of()));
        }
        PluginPlanResult result = plugin.plan(new PluginPlanRequest(List.of(new PluginFile("", bytes())), last, change, binding.plugin().args()));
        return switch (result) {
            case null -> throw new IllegalStateException("The plugin returned no plan.");
            case PluginPlanResult.Refused refused -> new PlanResult.Refused(refused.reason());
            case PluginPlanResult.Planned planned when planned.splices().stream().anyMatch(s -> !s.file().isEmpty()) ->
                new PlanResult.Refused("The plugin planned a change to another file than the body.");
            case PluginPlanResult.Planned planned -> new PlanResult.Planned(new Edit(order(planned.splices().stream().map(PluginSplice::splice).toList())));
        };
    }

    public PlanResult change(ModelChange change) {
        PlanResult result = plan(change);
        if (result instanceof PlanResult.Planned planned) {
            apply(planned.edit());
        }
        return result;
    }

    /** Hands the body's bytes to the host's atomic writer, as {@link etalii.adp.fbl.history.OpenBody#save} does; never a read-only body. */
    public void save(Consumer<byte[]> write) {
        Objects.requireNonNull(write, "write");
        if (isReadOnly()) {
            throw new IllegalStateException("A read-only body is never written.");
        }
        write.accept(bytes());
    }

    private String missingReason() {
        return "The plugin '" + binding.plugin().plugin() + "' that reads this file is not installed, so it is opened read-only.";
    }

    private PluginReadResult readNow() {
        if (plugin == null) {
            Finding finding = new Finding(FindingCodes.PLUGIN_MISSING, FindingSeverity.WARNING, missingReason(), new SourceLocation(fileName, 1, 1, 0));
            return new PluginReadResult(List.of(), List.of(finding), false);
        }
        return plugin.read(new PluginReadRequest(List.of(new PluginFile("", bytes())), binding.plugin().args()));
    }

    /** A plugin's splices in body order, as FBL §6.5 applies them; overlapping splices are the plugin's error. */
    private static List<Splice> order(List<Splice> splices) {
        // By start, those at one offset in the order the plugin planned them: the sort is stable.
        List<Splice> ordered = new ArrayList<>(splices);
        ordered.sort(Comparator.comparingInt(Splice::start));
        for (int i = 1; i < ordered.size(); i++) {
            if (ordered.get(i).start() < ordered.get(i - 1).end()) {
                throw new IllegalStateException("The plugin planned overlapping splices: " + ordered.get(i - 1) + " and " + ordered.get(i) + ".");
            }
        }
        return ordered;
    }

    @Override
    protected void reread() {
        last = readNow();
    }
}
