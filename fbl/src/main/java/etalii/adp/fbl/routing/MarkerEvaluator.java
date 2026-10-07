package etalii.adp.fbl.routing;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import etalii.adp.fbl.document.BindingReader;
import etalii.adp.fbl.document.Marker;
import etalii.adp.fbl.expression.BoundedRegex;
import etalii.adp.fbl.family.yaml.YamlParser;
import etalii.adp.fbl.text.BodyText;
import etalii.adp.fbl.text.TextLine;

/**
 * Evaluates a marker (FBL §12.2) on a body's bytes without reading it through a binding: a root key
 * of a yaml or json body, a prefix of the first line after a byte-order mark, or an expression one
 * of the first lines matches.
 */
public final class MarkerEvaluator {

    /** The number of lines a pattern marker looks at when it names none (FBL §12.2). */
    public static final int DEFAULT_LINES = 20;

    private MarkerEvaluator() {
    }

    public static boolean matches(Marker marker, byte[] bytes) {
        return matches(marker, bytes, null);
    }

    /** @param regexTimeout the bound on one match of a pattern marker, or null for 250 milliseconds */
    public static boolean matches(Marker marker, byte[] bytes, Duration regexTimeout) {
        Objects.requireNonNull(marker, "marker");
        Objects.requireNonNull(bytes, "bytes");
        BodyText text = new BodyText(bytes);
        if (!text.isValidUtf8()) {
            return false;
        }
        if (marker.rootKey() != null) {
            return rootKey(text, marker.rootKey(), marker.rootValue() != null ? BindingReader.scalarText(marker.rootValue()) : null);
        }
        if (marker.firstLine() != null) {
            List<TextLine> lines = text.lines();
            return !lines.isEmpty()
                    && text.text(Math.max(lines.get(0).start(), text.bomLength()), lines.get(0).contentEnd()).startsWith(marker.firstLine());
        }
        if (marker.pattern() != null) {
            BoundedRegex regex = new BoundedRegex(marker.pattern(), false, regexTimeout != null ? regexTimeout : Duration.ofMillis(250));
            int count = marker.lines() > 0 ? marker.lines() : DEFAULT_LINES;
            List<TextLine> lines = text.lines();
            for (int i = 0; i < lines.size() && i < count; i++) {
                TextLine line = lines.get(i);
                if (regex.isMatch(text.text(Math.max(line.start(), text.bomLength()), line.contentEnd()))) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * A root key of a yaml or json body (json is read as the yaml it also is), with its scalar value when one is asked.
     *
     * @param value the text the key's scalar must have, or null when the key's presence is enough
     */
    private static boolean rootKey(BodyText text, String key, String value) {
        List<YamlParser.RootKey> keys = YamlParser.rootKeys(text.bytes());
        if (keys == null) {
            return false;
        }
        for (YamlParser.RootKey found : keys) {
            if (!key.equals(found.name())) {
                continue;
            }
            return value == null || value.equals(found.value());
        }
        return false;
    }
}
