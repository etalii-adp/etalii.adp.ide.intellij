package etalii.adp.fbl.routing;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import etalii.adp.fbl.document.FblBinding;
import etalii.adp.fbl.document.TemplateSettings;
import etalii.adp.fbl.plugin.PersistencePlugin;
import etalii.adp.fbl.plugin.PluginTemplateRequest;

/**
 * New bodies from a binding's template (FBL §13): {@code template.byOrigin[origin]} before
 * {@code template.text}, else the plugin's {@code template} operation; the four placeholders replaced
 * and nothing else; and never an existing file overwritten.
 */
public final class TemplateWriter {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(name|base|key|newid:[A-Za-z_][A-Za-z0-9_-]*)\\}");

    private TemplateWriter() {
    }

    /** {@link #produce(FblBinding, String, String, Function, PersistencePlugin)} without a plugin. */
    public static byte[] produce(FblBinding binding, String origin, String fileName, Function<String, String> newId) {
        return produce(binding, origin, fileName, newId, null);
    }

    /**
     * The text of a new body named {@code fileName}, or null when the binding has no
     * template and no plugin was given to make one. {@code newId} makes an id by DISL's
     * strategy for a rule's type ({@code {newid:<rule>}}).
     *
     * @param origin the origin the body is made for, or null
     * @param plugin the plugin the caller has installed, or null
     */
    public static byte[] produce(FblBinding binding, String origin, String fileName, Function<String, String> newId, PersistencePlugin plugin) {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(newId, "newId");
        TemplateSettings template = binding.template();
        String text = template == null
                ? null
                : origin != null && template.byOrigin().containsKey(origin) ? template.byOrigin().get(origin) : template.text();
        if (text != null) {
            return replace(text, fileName, newId).getBytes(UTF_8);
        }
        if (binding.plugin() != null && plugin != null) {
            return plugin.template(new PluginTemplateRequest(FileNames.name(fileName), placeholders(fileName)));
        }
        return null;
    }

    /** Replaces the four placeholder forms of FBL §13 and leaves every other brace literal. */
    public static String replace(String template, String fileName, Function<String, String> newId) {
        Objects.requireNonNull(template, "template");
        Map<String, String> values = placeholders(fileName);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder builder = new StringBuilder(template.length());
        while (matcher.find()) {
            String name = matcher.group(1);
            String value = name.startsWith("newid:") ? newId.apply(name.substring(6)) : values.get(name);
            // The value is text and never a pattern of its own: a template is not evaluated.
            matcher.appendReplacement(builder, Matcher.quoteReplacement(value));
        }
        return matcher.appendTail(builder).toString();
    }

    /** The values of {@code {name}}, {@code {base}} and {@code {key}} for a new file. */
    public static Map<String, String> placeholders(String fileName) {
        String name = FileNames.name(fileName);
        String baseName = FileNames.baseName(name);
        Map<String, String> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("base", baseName);
        values.put("key", key(baseName));
        return Collections.unmodifiableMap(values);
    }

    /**
     * {@code {key}} (FBL §13): every character outside ASCII letters, digits and {@code _} replaced by
     * {@code _}, leading and trailing {@code _} removed, {@code _} prefixed when it then starts with a
     * digit, and {@code untitled} when nothing is left.
     */
    public static String key(String baseName) {
        Objects.requireNonNull(baseName, "baseName");
        StringBuilder builder = new StringBuilder(baseName.length());
        for (int i = 0; i < baseName.length(); i++) {
            char c = baseName.charAt(i);
            builder.append(isAsciiLetterOrDigit(c) || c == '_' ? c : '_');
        }
        int start = 0;
        int end = builder.length();
        while (start < end && builder.charAt(start) == '_') {
            start++;
        }
        while (end > start && builder.charAt(end - 1) == '_') {
            end--;
        }
        String key = builder.substring(start, end);
        if (key.isEmpty()) {
            return "untitled";
        }
        return key.charAt(0) >= '0' && key.charAt(0) <= '9' ? "_" + key : key;
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }
}
