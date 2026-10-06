package etalii.adp.fbl.document;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import etalii.adp.fbl.family.json.JsonValue;

/** An attribute binding (FBL §5.2): a slot with the options that decide reading and writing. */
public final class AttributeBinding extends Slot {

    private final String empty;
    private final Map<String, String> absent;
    private final JsonValue defaultValue;
    private final Integer decimals;
    private final boolean keepTimePrecision;
    private final String style;
    private final ReferenceBinding reference;
    private final Map<String, String> map;
    private final Slot override;
    private final boolean htmlParagraphs;
    private final CreateChild create;
    private final String readOnly;

    /**
     * @param slot the place the value is read from and written to
     * @param empty "remove", "keep" or "refuse"; null for the default
     * @param absent per family name: "insert" or "refuse"
     * @param defaultValue the binding's {@code default}, or null
     * @param decimals null for "shortest", else the number of decimals
     * @param map wire value to model value, in document order; null when the binding has none
     * @param override the slot that overrides this one where it is present, or null
     * @param create xml: how a missing child element holding the value is written, or null
     * @param readOnly null when the value may be written; the reason (possibly empty) when it may not
     */
    public AttributeBinding(Slot slot, String empty, Map<String, String> absent, JsonValue defaultValue, Integer decimals, boolean keepTimePrecision,
            String style, ReferenceBinding reference, Map<String, String> map, Slot override, boolean htmlParagraphs, CreateChild create, String readOnly) {
        super(slot.key(), slot.xmlAttribute(), slot.text(), slot.child(), slot.group(), slot.parent(), slot.capture(), slot.value(), slot.word(), slot.flag());
        this.empty = empty;
        this.absent = Collections.unmodifiableMap(new LinkedHashMap<>(absent));
        this.defaultValue = defaultValue;
        this.decimals = decimals;
        this.keepTimePrecision = keepTimePrecision;
        this.style = style;
        this.reference = reference;
        this.map = map == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(map));
        this.override = override;
        this.htmlParagraphs = htmlParagraphs;
        this.create = create;
        this.readOnly = readOnly;
    }

    /** A binding that is only its slot, every option at its default. */
    public static AttributeBinding of(Slot slot) {
        return new AttributeBinding(slot, null, Map.of(), null, null, false, null, null, null, null, false, null, null);
    }

    public String empty() {
        return empty;
    }

    public Map<String, String> absent() {
        return absent;
    }

    public JsonValue defaultValue() {
        return defaultValue;
    }

    public Integer decimals() {
        return decimals;
    }

    public boolean keepTimePrecision() {
        return keepTimePrecision;
    }

    public String style() {
        return style;
    }

    public ReferenceBinding reference() {
        return reference;
    }

    public Map<String, String> map() {
        return map;
    }

    public Slot override() {
        return override;
    }

    public boolean htmlParagraphs() {
        return htmlParagraphs;
    }

    public CreateChild create() {
        return create;
    }

    public String readOnly() {
        return readOnly;
    }

    @Override
    public boolean equals(Object other) {
        return super.equals(other) && other instanceof AttributeBinding binding && keepTimePrecision == binding.keepTimePrecision
                && htmlParagraphs == binding.htmlParagraphs && Objects.equals(empty, binding.empty) && absent.equals(binding.absent)
                && defaultValue == binding.defaultValue && Objects.equals(decimals, binding.decimals) && Objects.equals(style, binding.style)
                && Objects.equals(reference, binding.reference) && Objects.equals(map, binding.map) && Objects.equals(override, binding.override)
                && Objects.equals(create, binding.create) && Objects.equals(readOnly, binding.readOnly);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), empty, absent, decimals, keepTimePrecision, style, reference, map, override, htmlParagraphs, create, readOnly);
    }
}
