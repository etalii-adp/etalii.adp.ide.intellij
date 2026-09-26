package etalii.adp.drawio;

import java.util.ArrayList;
import java.util.List;

/**
 * A draw.io {@code style} attribute: {@code name;key=value;...} (research R20). Edited one key at a
 * time, keeping the order, every unknown key and a trailing {@code ;} as they are.
 */
public record Style(String text) {

    public Style {
        text = text == null ? "" : text;
    }

    /** The value of {@code key}, or {@code null} when absent or a flag. */
    public String get(String key) {
        return parts().stream().filter(p -> p.startsWith(key + "=")).map(p -> p.substring(key.length() + 1)).findFirst().orElse(null);
    }

    /** True when {@code name} is a flag, such as {@code ellipse}, or a key. */
    public boolean has(String name) {
        return parts().contains(name) || get(name) != null;
    }

    /** The style's name, its first part when that is a flag, or {@code null}. */
    public String name() {
        return parts().get(0).isEmpty() || parts().get(0).contains("=") ? null : parts().get(0);
    }

    /** This style with {@code key} set, or removed when {@code value} is {@code null} or empty; a new key goes before a trailing {@code ;}. */
    public Style with(String key, String value) {
        List<String> parts = parts();
        int at = parts.indexOf(key + "=" + get(key));
        boolean set = value != null && !value.isEmpty();
        if (at >= 0) {
            parts.remove(at);
        }
        if (set) {
            parts.add(at >= 0 ? at : parts.get(parts.size() - 1).isEmpty() ? parts.size() - 1 : parts.size(), key + "=" + value);
        }
        return new Style(parts.size() == 1 && parts.get(0).isEmpty() ? "" : String.join(";", parts));
    }

    private List<String> parts() {
        return new ArrayList<>(List.of(text.split(";", -1)));
    }
}
