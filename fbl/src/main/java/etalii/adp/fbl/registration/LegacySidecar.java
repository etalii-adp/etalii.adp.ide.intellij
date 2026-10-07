package etalii.adp.fbl.registration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.IntFunction;

import etalii.adp.fbl.Edit;
import etalii.adp.fbl.FblOptions;
import etalii.adp.fbl.Splice;
import etalii.adp.fbl.SpliceOperation;
import etalii.adp.fbl.document.BodySettings;
import etalii.adp.fbl.document.Claims;
import etalii.adp.fbl.document.Family;
import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.RegistrationSettings;
import etalii.adp.fbl.document.TextDefaults;
import etalii.adp.fbl.family.json.JsonFamily;
import etalii.adp.fbl.history.SplicedFile;
import etalii.adp.fbl.plan.NewText;
import etalii.adp.fbl.plan.PlanResult;
import etalii.adp.fbl.routing.FileNames;
import etalii.adp.fbl.rule.Entry;
import etalii.adp.fbl.rule.TreeEntry;
import etalii.adp.fbl.rule.TreeValue;
import etalii.adp.fbl.rule.ValueKind;
import etalii.adp.fbl.text.BodyText;

/**
 * A sidecar file hosts wrote before FBL (FBL §8.7): a legacy layout, keyed by view key and then by
 * element id with {@code {"x", "y"}}, or legacy identities, a natural key mapped to an id. It is read
 * when the registration has no matching block and written back by json splices while it exists;
 * this library never creates one.
 */
public final class LegacySidecar extends SplicedFile {

    private static final FblBinding JSON = new FblBinding(
            "sidecar",
            null,
            new Claims(List.of(), List.of(), false, false, null, List.of(), List.of(), Map.of()),
            new BodySettings(false, Family.JSON, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), BodySettings.DEFAULT_SETTLE),
            null,
            null,
            TextDefaults.DEFAULT,
            null,
            null,
            false,
            List.of(),
            List.of(),
            List.of(),
            RegistrationSettings.DEFAULT,
            null);

    private JsonFamily reading;

    private LegacySidecar(byte[] bytes) {
        super(bytes);
        reread();
    }

    /** Whether the sidecar is not one JSON object; it is then neither applied nor written. */
    public boolean isUnreadable() {
        return reading.unreadable() != null || reading.root() == null || reading.root().value().kind() != ValueKind.MAPPING;
    }

    public static LegacySidecar open(byte[] bytes) {
        return new LegacySidecar(Objects.requireNonNull(bytes, "bytes"));
    }

    /**
     * The sidecar path a registration's binding names ({@code {base}.layout.json} and the like), with
     * {@code {base}} the body's base name, beside the body.
     */
    public static Path pathFor(String pattern, Path bodyPath) {
        Objects.requireNonNull(pattern, "pattern");
        Path body = bodyPath.toAbsolutePath().normalize();
        return body.getParent().resolve(pattern.replace("{base}", FileNames.baseName(body.getFileName().toString())));
    }

    /**
     * The positions of one view, its key matched ignoring case (FBL §8.7); without a view, the first.
     *
     * @param view the view's key, or null
     */
    public Map<String, RegistrationEntry.Position> positions(String view) {
        Map<String, RegistrationEntry.Position> positions = new LinkedHashMap<>();
        TreeEntry entry = view(view);
        if (entry == null || entry.value().kind() != ValueKind.MAPPING) {
            return Collections.unmodifiableMap(positions);
        }
        for (TreeEntry element : entry.value().entries()) {
            if (element.name() == null || element.value().kind() != ValueKind.MAPPING) {
                continue;
            }
            Double x = number(element.value(), "x");
            Double y = number(element.value(), "y");
            if (x != null && y != null) {
                positions.put(element.name(), new RegistrationEntry.Position(x, y));
            }
        }
        return Collections.unmodifiableMap(positions);
    }

    /** Legacy identities: natural key to id. */
    public Map<String, String> identities() {
        Map<String, String> identities = new LinkedHashMap<>();
        if (isUnreadable()) {
            return Collections.unmodifiableMap(identities);
        }
        for (TreeEntry entry : reading.root().value().entries()) {
            if (entry.name() != null && entry.value().typed() instanceof String id) {
                identities.put(entry.name(), id);
            }
        }
        return Collections.unmodifiableMap(identities);
    }

    /**
     * Places an element in a view: its numbers replaced in place, or a new member at the end of the view's object.
     *
     * @param view the view's key, or null for the first view
     */
    public PlanResult planPlace(String view, String id, double x, double y) {
        Objects.requireNonNull(id, "id");
        if (isUnreadable()) {
            return new PlanResult.Refused("The layout file could not be read, so it is not written.");
        }
        String xText = NewText.number(x, 3);
        String yText = NewText.number(y, 3);
        TreeEntry viewEntry = view(view);
        if (viewEntry == null) {
            if (view == null) {
                return new PlanResult.Refused("The layout file has no view to place the element in.");
            }
            return insert(reading.root().value(), quoted(view),
                    indent -> "{" + lines(indent, quoted(id) + ": " + position(indent + step(), xText, yText)) + "}");
        }
        if (viewEntry.value().kind() != ValueKind.MAPPING) {
            return new PlanResult.Refused("The layout file's view is not an object.");
        }
        TreeEntry existing = viewEntry.value().member(id);
        if (existing != null && existing.value().kind() == ValueKind.MAPPING) {
            TreeEntry xMember = existing.value().member("x");
            TreeEntry yMember = existing.value().member("y");
            if (xMember != null && xMember.value().kind() == ValueKind.SCALAR && yMember != null && yMember.value().kind() == ValueKind.SCALAR) {
                List<Splice> splices = new ArrayList<>();
                if (!reading.text().text(xMember.value().span()).equals(xText)) {
                    splices.add(new Splice(SpliceOperation.REPLACE_VALUE, xMember.value().span().start(), xMember.value().span().end(), xText));
                }
                if (!reading.text().text(yMember.value().span()).equals(yText)) {
                    splices.add(new Splice(SpliceOperation.REPLACE_VALUE, yMember.value().span().start(), yMember.value().span().end(), yText));
                }
                return new PlanResult.Planned(new Edit(splices));
            }
        }
        return insert(viewEntry.value(), quoted(id), indent -> position(indent, xText, yText));
    }

    /** Stores an id for a natural key: replaced in place, or a new member at the end of the object. */
    public PlanResult planIdentify(String key, String id) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(id, "id");
        if (isUnreadable()) {
            return new PlanResult.Refused("The identities file could not be read, so it is not written.");
        }
        TreeEntry existing = reading.root().value().member(key);
        if (existing != null) {
            String text = JsonFamily.quote(id);
            return new PlanResult.Planned(new Edit(reading.text().text(existing.value().span()).equals(text)
                    ? List.of()
                    : List.of(new Splice(SpliceOperation.REPLACE_VALUE, existing.value().span().start(), existing.value().span().end(), text))));
        }
        return insert(reading.root().value(), JsonFamily.quote(key), indent -> JsonFamily.quote(id));
    }

    /** Applies what {@code plan} plans against this sidecar: the planned edit, or the refusal with nothing written. */
    public PlanResult change(Function<LegacySidecar, PlanResult> plan) {
        Objects.requireNonNull(plan, "plan");
        PlanResult result = plan.apply(this);
        if (result instanceof PlanResult.Planned planned) {
            apply(planned.edit());
        }
        return result;
    }

    @Override
    protected void reread() {
        reading = new JsonFamily(new BodyText(bytes()), JSON, FblOptions.DEFAULT);
        reading.parse();
    }

    /** The view of that key, ignoring case, or the first without a key; null when there is none. */
    private TreeEntry view(String view) {
        if (isUnreadable()) {
            return null;
        }
        for (TreeEntry member : reading.root().value().entries()) {
            if (view == null || view.equalsIgnoreCase(member.name())) {
                return member;
            }
        }
        return null;
    }

    private static Double number(TreeValue mapping, String key) {
        TreeEntry member = mapping.member(key);
        if (member == null) {
            return null;
        }
        return switch (member.value().typed()) {
            case Long l -> l.doubleValue();
            case Double d -> d;
            case null, default -> null;
        };
    }

    private static String quoted(String value) {
        return JsonFamily.quote(value);
    }

    /** The indentation step of the file: a member's indentation minus its object's, else two. */
    private int step() {
        for (Entry each : reading.entries()) {
            if (each instanceof TreeEntry entry && entry.parent() instanceof TreeEntry parent && !parent.isRoot()
                    && entry.lineSpan() != null && parent.lineSpan() != null && entry.indent() > parent.indent()) {
                return entry.indent() - parent.indent();
            }
        }
        return 2;
    }

    private String newline() {
        String dominant = reading.text().dominantEnding();
        return dominant != null ? dominant : "\n";
    }

    private String lines(int indent, String member) {
        return newline() + " ".repeat(indent + step()) + member + newline() + " ".repeat(indent);
    }

    private String position(int indent, String x, String y) {
        return "{" + newline() + " ".repeat(indent + step()) + "\"x\": " + x + "," + newline() + " ".repeat(indent + step()) + "\"y\": " + y + newline()
                + " ".repeat(indent) + "}";
    }

    /** A new member after the object's last, written as the file writes its members (FBL §6.3, json values). */
    private PlanResult insert(TreeValue container, String key, IntFunction<String> value) {
        if (container.entries().isEmpty()) {
            int indent = column(container.span().start()) + step();
            String text = newline() + " ".repeat(indent) + key + ": " + value.apply(indent) + newline() + " ".repeat(column(container.span().start()));
            return new PlanResult.Planned(
                    new Edit(List.of(new Splice(SpliceOperation.INSERT_ENTRY, container.span().start() + 1, container.span().start() + 1, text))));
        }
        TreeEntry last = container.entries().get(container.entries().size() - 1);
        String inserted = last.lineSpan() != null
                ? "," + newline() + " ".repeat(last.indent()) + key + ": " + value.apply(last.indent())
                : ", " + key + ": " + value.apply(last.indent());
        return new PlanResult.Planned(new Edit(List.of(new Splice(SpliceOperation.INSERT_ENTRY, last.own().end(), last.own().end(), inserted))));
    }

    private int column(int offset) {
        return offset - reading.text().lines().get(reading.text().lineIndexAt(offset)).start();
    }
}
