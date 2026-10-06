package etalii.adp.fbl.history;

import java.util.Objects;
import java.util.function.Consumer;

import etalii.adp.fbl.FblModel;
import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.plan.EditPlanner;
import etalii.adp.fbl.plan.ModelChange;
import etalii.adp.fbl.plan.PlanResult;
import etalii.adp.fbl.rule.BodyReading;

/**
 * One open body (FBL §9.1): its bytes, its reading through one binding, and the one history of its
 * edits. Re-reading after every edit, rather than patching the model, keeps one code path for what
 * a body means.
 */
public final class OpenBody extends SplicedFile {

    private final FblBinding binding;
    private final FblOptions options;
    private BodyReading reading;

    private OpenBody(byte[] bytes, FblBinding binding, FblOptions options) {
        super(bytes);
        this.binding = binding;
        this.options = options;
        reading = BodyReading.read(bytes, binding, options);
    }

    public FblBinding binding() {
        return binding;
    }

    public FblOptions options() {
        return options;
    }

    public FblModel model() {
        return reading.toModel();
    }

    /** An unreadable body (FBL §7.5) and a read-only binding (FBL §3.4) refuse every change and are never saved. */
    public boolean isReadOnly() {
        return reading.unreadable() != null || binding.readOnly() != null;
    }

    /** The reading of the current bytes. */
    public BodyReading reading() {
        return reading;
    }

    public static OpenBody open(byte[] bytes, FblBinding binding) {
        return open(bytes, binding, null);
    }

    /** @param options the caller's settings, or null for the defaults */
    public static OpenBody open(byte[] bytes, FblBinding binding, FblOptions options) {
        Objects.requireNonNull(bytes, "bytes");
        Objects.requireNonNull(binding, "binding");
        return new OpenBody(bytes, binding, options != null ? options : FblOptions.DEFAULT);
    }

    /** Plans {@code change} against the current bytes without applying it. */
    public PlanResult plan(ModelChange change) {
        return EditPlanner.plan(reading, change);
    }

    /** Plans and applies {@code change}: the planned edit, or the refusal with nothing written. */
    public PlanResult change(ModelChange change) {
        PlanResult result = plan(change);
        if (result instanceof PlanResult.Planned planned) {
            apply(planned.edit());
        }
        return result;
    }

    /**
     * Hands the body's bytes to {@code write}, the host's atomic writer (FBL §6.6). The library
     * writes no file itself: it opens no file for writing, so that the host keeps the one place
     * its files are written from.
     */
    public void save(Consumer<byte[]> write) {
        Objects.requireNonNull(write, "write");
        if (reading.unreadable() != null) {
            throw new IllegalStateException("An unreadable body is never written (FBL §7.5).");
        }
        if (binding.readOnly() != null) {
            throw new IllegalStateException("A read-only body is never written (FBL §3.4).");
        }
        write.accept(bytes());
    }

    @Override
    protected void reread() {
        reading = BodyReading.read(bytes(), binding, options);
    }
}
